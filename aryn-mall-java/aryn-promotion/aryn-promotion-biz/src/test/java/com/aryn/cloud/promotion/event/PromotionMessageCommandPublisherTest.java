package com.aryn.cloud.promotion.event;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PromotionMessageCommandPublisherTest {

	@Test
	void couponNotificationIsDeferredUntilTransactionCommit() {
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		PromotionMessageCommandPublisher publisher = new PromotionMessageCommandPublisher(template, new ObjectMapper());
		CouponUser couponUser = new CouponUser().setId("coupon-user-1").setTenantId("tenant-1")
				.setUserId("member-1");
		CouponInfo couponInfo = new CouponInfo().setCouponName("夏日优惠券");

		TransactionSynchronizationManager.initSynchronization();
		try {
			publisher.couponReceived(couponUser, couponInfo);
			verify(template, never()).syncSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
					org.mockito.ArgumentMatchers.any(Message.class), eq(RocketMqConstants.TIME_OUT));
			for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
				synchronization.afterCommit();
			}
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}

		ArgumentCaptor<Message<MessageSendCommand>> captor = ArgumentCaptor.forClass(Message.class);
		verify(template).syncSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC), captor.capture(),
				eq(RocketMqConstants.TIME_OUT));
		MessageSendCommand command = captor.getValue().getPayload();
		assertThat(command.getEventId()).isEqualTo("coupon-received:coupon-user-1");
		assertThat(command.getTenantId()).isEqualTo("tenant-1");
		assertThat(command.getRecipientId()).isEqualTo("member-1");
	}

}
