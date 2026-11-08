package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.entity.PayEventOutbox;
import com.aryn.cloud.pay.mapper.PayEventOutboxMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付事件 outbox：业务事务内只落库，提交后由重投任务发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayEventOutboxService {

	private final PayEventOutboxMapper outboxMapper;

	private final PayEventOutboxTxService txService;

	private final RocketMQTemplate rocketMQTemplate;

	@Value("${aryn.pay.outbox.batch-size:100}")
	private int batchSize;

	@Value("${aryn.pay.outbox.max-attempts:20}")
	private int maxAttempts;

	@Value("${aryn.pay.outbox.stale-sending-seconds:120}")
	private long staleSendingSeconds;

	public void enqueue(String tenantId, String eventType, String businessKey, String topic, String payload) {
		String id = IdWorker.getIdStr();
		PayEventOutbox outbox = new PayEventOutbox();
		outbox.setId(id);
		outbox.setEventId(id);
		outbox.setTenantId(tenantId);
		outbox.setEventType(eventType);
		outbox.setBusinessKey(businessKey);
		outbox.setTopic(topic);
		outbox.setPayload(payload);
		outbox.setStatus("0");
		outbox.setAttempts(0);
		outbox.setNextRetryTime(LocalDateTime.now());
		outboxMapper.insert(outbox);
	}

	/**
	 * 单实例重投：先重置长时间停留在“投递中”的事件，再抢占待投递事件。
	 * 发送失败保留待投递并按指数退避重试；消费端必须按业务键/事件ID幂等。
	 */
	public int relayBatch() {
		txService.resetStaleSending(LocalDateTime.now().minusSeconds(staleSendingSeconds));
		List<PayEventOutbox> due = outboxMapper.selectDue(LocalDateTime.now(), batchSize);
		int sent = 0;
		for (PayEventOutbox event : due) {
			// 短事务抢占单条事件；发送在网络事务外进行
			if (!txService.claim(event.getId())) {
				continue;
			}
			ArynTenantContextHolder.removeTenantId();
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			try {
				rocketMQTemplate.syncSend(event.getTopic(), new GenericMessage<>(event.getPayload()),
						RocketMqConstants.TIME_OUT);
				try {
					txService.markSent(event.getId());
					sent++;
				}
				catch (RuntimeException markException) {
					// 已发送但标记失败：保持至少一次投递，下一轮重置/重投由消费端业务键幂等
					log.warn("支付事件已发送但标记失败 eventId={}，等待重投", event.getEventId());
				}
			}
			catch (Exception exception) {
				int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
				boolean dead = attempts >= maxAttempts;
				LocalDateTime next = LocalDateTime.now().plusSeconds(backoffSeconds(attempts));
				try {
					txService.markRetry(event.getId(), dead ? "2" : "0", next, truncate(exception.getMessage()));
				}
				catch (RuntimeException markException) {
					// 标记失败时保留 SENDING，由下一轮 resetStaleSending 收敛
					log.warn("支付事件投递和退避标记均失败 eventId={}", event.getEventId());
				}
				log.warn("支付事件投递失败 eventId={} type={} attempts={} dead={}", event.getEventId(),
						event.getEventType(), attempts, dead);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
		return sent;
	}

	private long backoffSeconds(int attempts) {
		long seconds = 5L * (1L << Math.min(attempts, 6));
		return Math.min(seconds, 300L);
	}

	private String truncate(String value) {
		if (value == null) {
			return null;
		}
		return value.length() > 480 ? value.substring(0, 480) : value;
	}

}
