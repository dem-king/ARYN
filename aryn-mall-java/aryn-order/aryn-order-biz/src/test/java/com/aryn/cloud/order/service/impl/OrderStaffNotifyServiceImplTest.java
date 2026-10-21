package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单事件工作人员通知测试：收件人口径、租户回落、跳转负载与失败降级。
 */
class OrderStaffNotifyServiceImplTest {

	private static final String TENANT_ID = "1590229800633634816";

	private RemoteMessageStaffService remoteMessageStaffService;

	private OrderInfoMapper orderInfoMapper;

	private RocketMQTemplate rocketMQTemplate;

	private OrderStaffNotifyServiceImpl service;

	@BeforeEach
	void setUp() {
		remoteMessageStaffService = mock(RemoteMessageStaffService.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		service = new OrderStaffNotifyServiceImpl(orderInfoMapper, rocketMQTemplate, new ObjectMapper());
		org.springframework.test.util.ReflectionTestUtils.setField(service, "remoteMessageStaffService",
				remoteMessageStaffService);
	}

	@Test
	void newOrderNotifiesEveryAdminWithOrderRoute() {
		stubAdmins(staff("9001", "张管理"), staff("9002", "李管理"));
		OrderInfo order = order("1001");

		service.notifyOrderCreated(order);

		List<MessageSendCommand> commands = capturedCommands(2);
		assertThat(commands).allSatisfy(command -> {
			assertThat(command.getEventId()).isEqualTo("order-created:admin:1001");
			assertThat(command.getTenantId()).isEqualTo(TENANT_ID);
			assertThat(command.getRecipientType()).isEqualTo(MessageIdentityType.SYS_USER.name());
			assertThat(command.getCategory()).isEqualTo("ORDER");
			assertThat(command.getBizType()).isEqualTo("ORDER_CREATE");
			assertThat(command.getBizId()).isEqualTo("1001");
			assertThat(command.getChannels()).containsExactly("IN_APP");
			// jump_payload 落 json 列且消费端要求 JSON 对象，裸路由会整条消息被拒（DLQ）
			assertThat(command.getJumpPayload()).startsWith("{").endsWith("}")
				.contains("\"route\":\"/order/detail?id=1001\"");
			// 卡片负载带业务标识，金额仍走 amount 键供前端展示
			assertThat(command.getCardPayload()).startsWith("{").endsWith("}")
				.contains("\"bizType\":\"ORDER_CREATE\"")
				.contains("\"bizId\":\"1001\"")
				.contains("\"amount\":\"1800\"");
			assertThat(command.getContent()).contains("1001").contains("1800");
		});
		assertThat(commands).extracting(MessageSendCommand::getRecipientId).containsExactly("9001", "9002");
		assertThat(commands).extracting(MessageSendCommand::getRecipientName).containsExactly("张管理", "李管理");
	}

	@Test
	void cashOnDeliveryOrderMentionsCodInSummary() {
		stubAdmins(staff("9001", "张管理"));
		OrderInfo order = order("1002");

		service.notifyOrderCreated(order);

		MessageSendCommand command = capturedCommands(1).get(0);
		assertThat(command.getSummary()).contains("货到付款");
		assertThat(command.getContent()).contains("货到付款订单");
	}

	@Test
	void prepaidOrderMentionsPendingPayment() {
		stubAdmins(staff("9001", "张管理"));
		OrderInfo order = order("1003").setPaymentType("1");

		service.notifyOrderCreated(order);

		MessageSendCommand command = capturedCommands(1).get(0);
		assertThat(command.getSummary()).contains("待支付");
		assertThat(command.getContent()).contains("请关注买家支付");
	}

	@Test
	void cancelNotifiesWithSourceSpecificTitle() {
		stubAdmins(staff("9001", "张管理"));
		OrderInfo order = order("1004");

		service.notifyOrderCanceled(order, OrderStaffNotifyService.CancelSource.BUYER);

		MessageSendCommand buyer = capturedCommands(1).get(0);
		assertThat(buyer.getEventId()).isEqualTo("order-canceled:admin:1004");
		assertThat(buyer.getBizType()).isEqualTo("ORDER_CANCEL");
		assertThat(buyer.getTitle()).isEqualTo("买家取消订单");
		assertThat(buyer.getContent()).contains("取消来源：买家取消订单");
		assertThat(buyer.getJumpPayload()).contains("\"route\":\"/order/detail?id=1004\"");
	}

	@Test
	void timeoutCancelUsesTimeoutTitleAndHint() {
		stubAdmins(staff("9001", "张管理"));

		service.notifyOrderCanceled(order("1005"), OrderStaffNotifyService.CancelSource.TIMEOUT);

		MessageSendCommand command = capturedCommands(1).get(0);
		assertThat(command.getTitle()).isEqualTo("订单超时自动取消");
		assertThat(command.getContent()).contains("长期未支付");
	}

	@Test
	void adminCancelUsesAdminTitle() {
		stubAdmins(staff("9001", "张管理"));

		service.notifyOrderCanceled(order("1006"), OrderStaffNotifyService.CancelSource.ADMIN);

		assertThat(capturedCommands(1).get(0).getTitle()).isEqualTo("管理端取消订单");
	}

	@Test
	void refundApplyNotifiesWithRefundRouteAndOrderNo() {
		stubAdmins(staff("9001", "张管理"));
		OrderInfo order = order("1007");
		when(orderInfoMapper.selectById("1007")).thenReturn(order);
		OrderRefund refund = refund("8001", "1007");

		service.notifyRefundApplied(refund);

		MessageSendCommand command = capturedCommands(1).get(0);
		assertThat(command.getEventId()).isEqualTo("order-refund-apply:admin:8001");
		assertThat(command.getBizType()).isEqualTo("ORDER_REFUND_APPLY");
		assertThat(command.getBizId()).isEqualTo("8001");
		assertThat(command.getContent()).contains("NO-1007").contains("秒杀商品不符合预期");
		assertThat(command.getJumpPayload()).startsWith("{").endsWith("}")
			.contains("\"route\":\"/order/order-refund?refundId=8001\"");
		// 退款单 ID 走 bizId，不再占用 cardPayload 的 orderId 键
		assertThat(command.getCardPayload()).startsWith("{").endsWith("}")
			.contains("\"bizType\":\"ORDER_REFUND_APPLY\"")
			.contains("\"bizId\":\"8001\"")
			.contains("\"amount\":\"99.00\"")
			.doesNotContain("orderId");
	}

	@Test
	void refundApplyFallsBackToOrderIdWhenLookupFails() {
		stubAdmins(staff("9001", "张管理"));
		when(orderInfoMapper.selectById(anyString())).thenThrow(new RuntimeException("db down"));
		OrderRefund refund = refund("8002", "1008");

		service.notifyRefundApplied(refund);

		// 回查失败不能吞掉整条通知：订单号退回订单 ID 展示
		assertThat(capturedCommands(1).get(0).getContent()).contains("1008");
	}

	@Test
	void entityTenantWinsButFallsBackToContextHolder() {
		stubAdmins(staff("9001", "张管理"));
		// 实体带租户时优先用实体的值
		service.notifyOrderCreated(order("1009"));
		assertThat(capturedCommands(1).get(0).getTenantId()).isEqualTo(TENANT_ID);

		// 实体租户为空（创建链路常见）时退回当前租户上下文
		ArynTenantContextHolder.setTenantId("ctx-tenant");
		try {
			when(remoteMessageStaffService.queryRecipientsByRoleCode("ctx-tenant", CommonConstants.ROLE_ADMIN_CODE))
				.thenReturn(List.of(staff("9001", "张管理")));
			service.notifyOrderCreated(order("1014").setTenantId(null));
			ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
			verify(rocketMQTemplate, times(2)).convertAndSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
					captor.capture());
			assertThat(((MessageSendCommand) captor.getAllValues().get(1)).getTenantId()).isEqualTo("ctx-tenant");
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	@Test
	void skipsSilentlyWhenTenantMissingEverywhere() {
		OrderInfo order = order("1010").setTenantId(null);
		ArynTenantContextHolder.removeTenantId();

		service.notifyOrderCreated(order);

		verify(remoteMessageStaffService, never()).queryRecipientsByRoleCode(anyString(), anyString());
		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void skipsWhenNoAdminFound() {
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of());

		service.notifyOrderCreated(order("1011"));

		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void recipientLookupFailureDoesNotPropagate() {
		when(remoteMessageStaffService.queryRecipientsByRoleCode(anyString(), anyString()))
			.thenThrow(new RuntimeException("upms down"));

		service.notifyOrderCreated(order("1012"));

		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void mqFailureDoesNotPropagate() {
		stubAdmins(staff("9001", "张管理"));
		doThrow(new RuntimeException("broker down")).when(rocketMQTemplate).convertAndSend(anyString(),
				any(Object.class));

		service.notifyOrderCreated(order("1013"));
		// 不抛异常即通过：通知失败不能影响订单事实
	}

	@Test
	void nullEntityIsIgnored() {
		service.notifyOrderCreated(null);
		service.notifyOrderCanceled(null, OrderStaffNotifyService.CancelSource.BUYER);
		service.notifyRefundApplied(null);

		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	private void stubAdmins(StaffMessageRecipientVO... admins) {
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of(admins));
	}

	@SuppressWarnings("unchecked")
	private List<MessageSendCommand> capturedCommands(int expectedCount) {
		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate, times(expectedCount)).convertAndSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
				captor.capture());
		return captor.getAllValues().stream().map(payload -> (MessageSendCommand) payload).toList();
	}

	private OrderInfo order(String id) {
		return new OrderInfo().setId(id)
			.setUserId("user-1")
			.setOrderNo("NO-" + id)
			.setTenantId(TENANT_ID)
			.setPaymentType("3")
			.setStatus("1")
			.setPayStatus("0")
			.setPaymentPrice(new BigDecimal("1800"))
			.setTotalPrice(new BigDecimal("1800"))
			.setRecipientName("李船长");
	}

	private OrderRefund refund(String id, String orderId) {
		OrderRefund refund = new OrderRefund();
		refund.setId(id);
		refund.setOrderId(orderId);
		refund.setRefundTradeNo("RF-" + id);
		refund.setRefundAmount(new BigDecimal("99.00"));
		refund.setRefundReason("秒杀商品不符合预期");
		refund.setTenantId(TENANT_ID);
		return refund;
	}

	private StaffMessageRecipientVO staff(String id, String nickname) {
		StaffMessageRecipientVO vo = new StaffMessageRecipientVO();
		vo.setId(id);
		vo.setNickname(nickname);
		return vo;
	}

}
