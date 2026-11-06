package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationContextVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.api.vo.conversation.OrderContextItemVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.service.impl.ConversationContextServiceImpl;
import com.aryn.cloud.order.api.remote.RemoteOrderContextService;
import com.aryn.cloud.order.api.vo.OrderContextVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P2-A 会员/订单上下文：message 域聚合接口测试（mock 会话服务/订单远程，不依赖 DB/Dubbo）。
 *
 * <p>覆盖：会员快照回退、订单映射与条数约定（3）、私信会话不查订单、订单域异常降级空数组、
 * 参与者鉴权失败时非主管抛错、主管走全租户只读降级、会话不存在分支。</p>
 */
class ConversationContextServiceTest {

	private static final String TENANT = "tenant-1";
	private static final String CONVERSATION = "conversation-1";

	private ConversationService conversationService;
	private MessageConversationMapper conversationMapper;
	private RemoteOrderContextService orderContextService;
	private ConversationContextServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationService = mock(ConversationService.class);
		conversationMapper = mock(MessageConversationMapper.class);
		orderContextService = mock(RemoteOrderContextService.class);
		service = new ConversationContextServiceImpl(conversationService, conversationMapper, orderContextService);
	}

	@Test
	void assemblesMemberOrdersAndConversation() {
		ConversationVO view = customerServiceConversation("member-1", "王小雅", "avatar.png");
		view.setContextPayload("{\"orderId\":\"o-1\"}");
		LocalDateTime createTime = LocalDateTime.of(2026, 11, 6, 10, 30, 0);
		when(conversationService.get(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION)).thenReturn(view);
		OrderContextVO order = order("order-1", "NO-1", "2", "待发货", "1", new BigDecimal("88.80"));
		order.setCreateTime(createTime);
		order.setDeliveryWay("1");
		when(orderContextService.queryRecentOrders(TENANT, "member-1", 3)).thenReturn(List.of(order));

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-1",
				CONVERSATION, false);

		// 会话摘要直接复用 ConversationVO，contextPayload 与 conversation.contextPayload 同值。
		assertNotNull(context.getConversation());
		assertEquals("{\"orderId\":\"o-1\"}", context.getContextPayload());
		assertEquals("member-1", context.getMember().getId());
		assertEquals("王小雅", context.getMember().getName());
		assertEquals("avatar.png", context.getMember().getAvatar());
		assertEquals(1, context.getOrders().size());
		OrderContextItemVO item = context.getOrders().get(0);
		assertEquals("order-1", item.getOrderId());
		assertEquals("NO-1", item.getOrderNo());
		assertEquals("2", item.getStatus());
		assertEquals("待发货", item.getStatusName());
		assertEquals("1", item.getPayStatus());
		assertEquals(new BigDecimal("88.80"), item.getPayAmount());
		assertEquals(createTime, item.getCreateTime());
		assertEquals("1", item.getDeliveryWay());
		// 契约约定最多 3 条。
		verify(orderContextService).queryRecentOrders(TENANT, "member-1", 3);
	}

	@Test
	void memberNameFallsBackToCustomerIdWhenSnapshotMissing() {
		ConversationVO view = customerServiceConversation("member-1", "  ", null);
		when(conversationService.get(any(), any(), any(), any())).thenReturn(view);
		when(orderContextService.queryRecentOrders(TENANT, "member-1", 3)).thenReturn(List.of());

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-1",
				CONVERSATION, false);

		assertEquals("member-1", context.getMember().getId());
		assertEquals("member-1", context.getMember().getName());
		assertNull(context.getMember().getAvatar());
		assertTrue(context.getOrders().isEmpty());
	}

	@Test
	void staffDirectConversationSkipsMemberAndOrderQuery() {
		ConversationVO view = new ConversationVO();
		view.setId(CONVERSATION);
		view.setConversationType(ConversationType.STAFF_DIRECT.name());
		when(conversationService.get(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION)).thenReturn(view);

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-1",
				CONVERSATION, false);

		assertNull(context.getMember());
		assertTrue(context.getOrders().isEmpty());
		// 私信会话不查订单域，避免无意义远程调用。
		verifyNoInteractions(orderContextService);
	}

	@Test
	void orderFailureDegradesToEmptyOrders() {
		ConversationVO view = customerServiceConversation("member-1", "王小雅", null);
		when(conversationService.get(any(), any(), any(), any())).thenReturn(view);
		when(orderContextService.queryRecentOrders(TENANT, "member-1", 3))
			.thenThrow(new RuntimeException("order service timeout"));

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-1",
				CONVERSATION, false);

		// 订单域异常降级为空数组，会员卡仍可用。
		assertNotNull(context.getMember());
		assertTrue(context.getOrders().isEmpty());
	}

	@Test
	void nullOrderResultDegradesToEmptyOrders() {
		ConversationVO view = customerServiceConversation("member-1", "王小雅", null);
		when(conversationService.get(any(), any(), any(), any())).thenReturn(view);
		when(orderContextService.queryRecentOrders(TENANT, "member-1", 3)).thenReturn(null);

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-1",
				CONVERSATION, false);

		assertTrue(context.getOrders().isEmpty());
	}

	@Test
	void nonParticipantWithoutSupervisorIsRejected() {
		when(conversationService.get(TENANT, MessageIdentityType.SYS_USER, "staff-9", CONVERSATION))
			.thenThrow(new ArynBusinessException("会话不存在或无权访问"));

		assertThrows(ArynBusinessException.class,
				() -> service.getContext(TENANT, MessageIdentityType.SYS_USER, "staff-9", CONVERSATION, false));
		verify(conversationMapper, never()).selectForTenant(any(), any());
		verifyNoInteractions(orderContextService);
	}

	@Test
	void supervisorFallsBackToTenantReadOnlyView() {
		when(conversationService.get(TENANT, MessageIdentityType.SYS_USER, "supervisor-1", CONVERSATION))
			.thenThrow(new ArynBusinessException("会话不存在或无权访问"));
		ConversationVO view = customerServiceConversation("member-1", "王小雅", "avatar.png");
		when(conversationMapper.selectForTenant(TENANT, CONVERSATION)).thenReturn(view);
		when(orderContextService.queryRecentOrders(TENANT, "member-1", 3)).thenReturn(List.of());

		ConversationContextVO context = service.getContext(TENANT, MessageIdentityType.SYS_USER, "supervisor-1",
				CONVERSATION, true);

		assertEquals(CONVERSATION, context.getConversation().getId());
		assertEquals("member-1", context.getMember().getId());
		verify(conversationMapper).selectForTenant(TENANT, CONVERSATION);
	}

	@Test
	void supervisorFallbackWithoutConversationIsRejected() {
		when(conversationService.get(any(), any(), any(), any()))
			.thenThrow(new ArynBusinessException("会话不存在或无权访问"));
		when(conversationMapper.selectForTenant(TENANT, CONVERSATION)).thenReturn(null);

		assertThrows(ArynBusinessException.class,
				() -> service.getContext(TENANT, MessageIdentityType.SYS_USER, "supervisor-1", CONVERSATION, true));
	}

	private ConversationVO customerServiceConversation(String customerId, String customerName, String avatar) {
		ConversationVO view = new ConversationVO();
		view.setId(CONVERSATION);
		view.setConversationType(ConversationType.CUSTOMER_SERVICE.name());
		view.setCustomerId(customerId);
		view.setCustomerName(customerName);
		view.setCustomerAvatar(avatar);
		view.setStatus("ACTIVE");
		view.setQueueCode("DEFAULT");
		return view;
	}

	private OrderContextVO order(String orderId, String orderNo, String status, String statusName, String payStatus,
			BigDecimal payAmount) {
		OrderContextVO order = new OrderContextVO();
		order.setOrderId(orderId);
		order.setOrderNo(orderNo);
		order.setStatus(status);
		order.setStatusName(statusName);
		order.setPayStatus(payStatus);
		order.setPayAmount(payAmount);
		return order;
	}

}
