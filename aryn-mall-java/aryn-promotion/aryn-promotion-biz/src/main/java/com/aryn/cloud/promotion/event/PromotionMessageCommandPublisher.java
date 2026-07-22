package com.aryn.cloud.promotion.event;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/** 营销事务提交后发送站内通知命令。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionMessageCommandPublisher {

	private final RocketMQTemplate rocketMQTemplate;
	private final ObjectMapper objectMapper;

	public void couponReceived(CouponUser couponUser, CouponInfo couponInfo) {
		sendAfterCommit(() -> {
			try {
				MessageSendCommand command = new MessageSendCommand();
				command.setEventId("coupon-received:" + couponUser.getId());
				command.setTenantId(couponUser.getTenantId());
				command.setRecipientType(MessageIdentityType.MALL_USER.name());
				command.setRecipientId(couponUser.getUserId());
				command.setCategory("PROMOTION");
				command.setTitle("优惠券已到账");
				command.setSummary(couponInfo.getCouponName());
				command.setContent("您领取的优惠券已到账，请在有效期内使用。");
				command.setBizType("COUPON_RECEIVE");
				command.setBizId(couponUser.getId());
				command.setJumpPayload(json(Map.of("bizType", "COUPON", "bizId", couponUser.getId())));
				rocketMQTemplate.syncSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, new GenericMessage<>(command),
						RocketMqConstants.TIME_OUT);
			}
			catch (RuntimeException exception) {
				log.error("优惠券领取站内通知发送失败，不回滚营销交易, couponUserId={}", couponUser.getId(), exception);
			}
		});
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("营销通知序列化失败", exception);
		}
	}

	private void sendAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

}
