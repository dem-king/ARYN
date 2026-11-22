package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.order.api.entity.DeliveryGroupOperation;
import com.aryn.cloud.order.mapper.DeliveryGroupOperationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 事务outbox至少一次发送；固定eventId由现有消息域去重。租约仅保护发送进度，不改变配送权限。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryGroupOutboxService {
    private final DeliveryGroupOperationMapper operations;
    private final ObjectMapper json;
    private final RocketMQTemplate mq;
    public void dispatch() {
        String tenant = ArynTenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new IllegalStateException("缺少outbox租户");
        var due = operations.selectList(Wrappers.<DeliveryGroupOperation>lambdaQuery().eq(DeliveryGroupOperation::getTenantId, tenant)
            .eq(DeliveryGroupOperation::getOutboxStatus, "PENDING").le(DeliveryGroupOperation::getNextAttemptAt, LocalDateTime.now())
            .and(query -> query.isNull(DeliveryGroupOperation::getLeaseUntil).or().lt(DeliveryGroupOperation::getLeaseUntil, LocalDateTime.now()))
            .orderByAsc(DeliveryGroupOperation::getNextAttemptAt).last("LIMIT 20"));
        for (var candidate : due) {
            String token = UUID.randomUUID().toString();
            int claimed = operations.update(null, Wrappers.<DeliveryGroupOperation>lambdaUpdate().eq(DeliveryGroupOperation::getTenantId, tenant)
                .eq(DeliveryGroupOperation::getId, candidate.getId()).eq(DeliveryGroupOperation::getOutboxStatus, "PENDING")
                .le(DeliveryGroupOperation::getNextAttemptAt, LocalDateTime.now())
                .and(query -> query.isNull(DeliveryGroupOperation::getLeaseUntil).or().lt(DeliveryGroupOperation::getLeaseUntil, LocalDateTime.now()))
                .set(DeliveryGroupOperation::getLeaseToken, token).set(DeliveryGroupOperation::getLeaseUntil, LocalDateTime.now().plusMinutes(2))
                .setSql("attempts=attempts+1"));
            if (claimed != 1) continue;
            try {
                var messages = json.readValue(candidate.getNotificationJson(), new TypeReference<List<DeliveryGroupService.Notification>>() { });
                for (var event : messages) {
                    if (heartbeat(tenant, candidate.getId(), token) != 1) throw new IllegalStateException("合送通知租约已失效");
                    var command = new MessageSendCommand(); command.setEventId(event.eventId()); command.setTenantId(tenant);
                    command.setRecipientType("MALL_USER"); command.setRecipientId(event.recipientId()); command.setCategory("ORDER");
                    command.setTitle("订单配送安排更新"); command.setSummary(event.summary()); command.setContent(event.summary());
                    command.setBizType("ORDER"); command.setBizId(event.orderId()); command.setChannels(List.of("IN_APP"));
                    var sent = mq.syncSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command, 5000);
                    if (sent == null || sent.getSendStatus() != SendStatus.SEND_OK) throw new IllegalStateException("消息发送未确认");
                }
                operations.update(null, Wrappers.<DeliveryGroupOperation>lambdaUpdate().eq(DeliveryGroupOperation::getTenantId, tenant)
                    .eq(DeliveryGroupOperation::getId, candidate.getId()).eq(DeliveryGroupOperation::getLeaseToken, token)
                    .set(DeliveryGroupOperation::getOutboxStatus, "SENT").set(DeliveryGroupOperation::getLeaseToken, null)
                    .set(DeliveryGroupOperation::getLeaseUntil, null).set(DeliveryGroupOperation::getUpdateTime, LocalDateTime.now()));
            } catch (Exception failure) {
                long delay = Math.min(3600, 5L << Math.min(9, candidate.getAttempts()));
                operations.update(null, Wrappers.<DeliveryGroupOperation>lambdaUpdate().eq(DeliveryGroupOperation::getTenantId, tenant)
                    .eq(DeliveryGroupOperation::getId, candidate.getId()).eq(DeliveryGroupOperation::getLeaseToken, token)
                    .set(DeliveryGroupOperation::getNextAttemptAt, LocalDateTime.now().plusSeconds(delay))
                    .set(DeliveryGroupOperation::getLeaseToken, null).set(DeliveryGroupOperation::getLeaseUntil, null));
                log.warn("合送组通知待重试；tenant={} operation={} errorType={}", tenant, candidate.getId(), failure.getClass().getSimpleName());
            }
        }
    }
    private int heartbeat(String tenant, String id, String token) {
        return operations.update(null, Wrappers.<DeliveryGroupOperation>lambdaUpdate().eq(DeliveryGroupOperation::getTenantId, tenant)
            .eq(DeliveryGroupOperation::getId, id).eq(DeliveryGroupOperation::getLeaseToken, token)
            .gt(DeliveryGroupOperation::getLeaseUntil, LocalDateTime.now()).set(DeliveryGroupOperation::getLeaseUntil, LocalDateTime.now().plusMinutes(2)));
    }
}
