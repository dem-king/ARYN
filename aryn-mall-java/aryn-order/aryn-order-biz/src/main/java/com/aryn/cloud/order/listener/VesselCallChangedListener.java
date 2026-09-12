package com.aryn.cloud.order.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 靠港计划变更消费者：计算影响面（未完成订单、收集中/待确认共享购物车），
 * 站内信提醒相关用户。
 *
 * <p>快照原则：已支付订单的配送上下文快照不自动改写，仅提醒用户重新确认安排；
 * 波次与司机任务由管理端工作台提示处理。重复投递以 eventId（变更日志ID）幂等。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.VESSEL_CALL_CHANGED_TOPIC,
        consumerGroup = "vessel-call-changed-consumer")
public class VesselCallChangedListener implements RocketMQListener<VesselCallChangedNotice> {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

	private final com.aryn.cloud.order.mapper.OrderInfoMapper orderInfoMapper;

	private final com.aryn.cloud.order.mapper.SharedCartMapper sharedCartMapper;

	private final org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

	@Override
	public void onMessage(VesselCallChangedNotice notice) {
		if (notice == null || !StringUtils.hasText(notice.getTenantId())
				|| !StringUtils.hasText(notice.getCallId())) {
			log.warn("靠港变更消息缺少关键字段，忽略");
			return;
		}
		try {
			// 影响面 1：未完成订单（已支付待发货/待收货）
			List<com.aryn.cloud.order.api.entity.OrderInfo> orders = orderInfoMapper.selectList(
					Wrappers.lambdaQuery(com.aryn.cloud.order.api.entity.OrderInfo.class)
							.eq(com.aryn.cloud.order.api.entity.OrderInfo::getTenantId, notice.getTenantId())
							.eq(com.aryn.cloud.order.api.entity.OrderInfo::getVesselCallId, notice.getCallId())
							.in(com.aryn.cloud.order.api.entity.OrderInfo::getStatus, List.of("2", "3"))
							.eq(com.aryn.cloud.order.api.entity.OrderInfo::getDelFlag, "0"));

			// 影响面 2：收集中/待确认共享购物车
			List<SharedCart> carts = sharedCartMapper.selectList(Wrappers.lambdaQuery(SharedCart.class)
					.eq(SharedCart::getTenantId, notice.getTenantId())
					.eq(SharedCart::getVesselCallId, notice.getCallId())
					.in(SharedCart::getStatus, List.of(SharedCart.STATUS_COLLECTING,
							SharedCart.STATUS_WAITING_CONFIRM))
					.eq(SharedCart::getDelFlag, "0"));

			// 收集去重后的收件人：订单下单用户 + 购物车发起人/确认人
			java.util.Set<String> recipientIds = new java.util.LinkedHashSet<>();
			orders.forEach(order -> {
				if (StringUtils.hasText(order.getUserId())) {
					recipientIds.add(order.getUserId());
				}
			});
			carts.forEach(cart -> {
				if (StringUtils.hasText(cart.getOwnerUserId())) {
					recipientIds.add(cart.getOwnerUserId());
				}
				if (StringUtils.hasText(cart.getConfirmerUserId())) {
					recipientIds.add(cart.getConfirmerUserId());
				}
			});

			String summary = buildChangeSummary(notice);
			for (String recipientId : recipientIds) {
				sendInAppNotice(notice, recipientId, summary,
						orders.stream().map(com.aryn.cloud.order.api.entity.OrderInfo::getOrderNo).limit(5).toList(),
						carts.size());
			}
			log.info("靠港计划[{}]变更提醒完成：订单 {} 单、购物车 {} 个、通知 {} 人",
					notice.getCallId(), orders.size(), carts.size(), recipientIds.size());
		}
		catch (Exception ex) {
			log.error("靠港变更提醒处理失败 callId={}", notice.getCallId(), ex);
			// 抛出让 MQ 重试；幂等由 eventId 保证
			throw ex instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(ex);
		}
	}

	private void sendInAppNotice(VesselCallChangedNotice notice, String recipientId, String summary,
			List<String> orderNos, int cartCount) {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("call-change:" + notice.getChangeLogId() + ":" + recipientId);
		command.setTenantId(notice.getTenantId());
		command.setRecipientType(com.aryn.cloud.message.api.enums.MessageIdentityType.MALL_USER.name());
		command.setRecipientId(recipientId);
		command.setCategory("VESSEL_CALL_CHANGE");
		command.setTitle("靠港计划变更通知");
		command.setSummary(summary);
		StringBuilder content = new StringBuilder("您船舶的靠港计划已变更：\n").append(summary);
		if (orderNos != null && !orderNos.isEmpty()) {
			content.append("\n涉及订单：").append(String.join("、", orderNos));
		}
		if (cartCount > 0) {
			content.append("\n涉及共享购物车 ").append(cartCount).append(" 个");
		}
		content.append("\n请进入小程序确认配送安排（配送时间窗以靠港计划为准）。");
		command.setContent(content.toString());
		command.setBizType("VESSEL_CALL");
		command.setBizId(notice.getCallId());
		command.setChannels(List.of("IN_APP"));
		rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
	}

	private String buildChangeSummary(VesselCallChangedNotice notice) {
		StringBuilder summary = new StringBuilder();
		if (changed(notice.getOldEta(), notice.getNewEta())) {
			summary.append("到港时间 ")
					.append(format(notice.getOldEta())).append(" → ").append(format(notice.getNewEta()))
					.append("；");
		}
		if (changed(notice.getOldEtd(), notice.getNewEtd())) {
			summary.append("离港时间 ")
					.append(format(notice.getOldEtd())).append(" → ").append(format(notice.getNewEtd()))
					.append("；");
		}
		if (notice.getOldBerth() != null && !notice.getOldBerth().equals(notice.getNewBerth())) {
			summary.append("泊位 ").append(notice.getOldBerth()).append(" → ").append(notice.getNewBerth())
					.append("；");
		}
		if (summary.length() == 0) {
			summary.append("靠港安排已调整；");
		}
		return summary.toString();
	}

	private boolean changed(Object before, Object after) {
		return !java.util.Objects.equals(before, after);
	}

	private String format(java.time.LocalDateTime time) {
		return time != null ? time.format(TIME_FORMAT) : "-";
	}

}
