package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * 货到付款收款预警的单元测试：阈值解析、轮次推进、双端收件人与幂等 eventId。
 */
class CodPayRemindServiceImplTest {

	private static final String TENANT_ID = "1590229800633634816";

	private IOrderInfoService orderInfoService;

	private IOrderConfigService orderConfigService;

	private RemoteMessageStaffService remoteMessageStaffService;

	private RocketMQTemplate rocketMQTemplate;

	private CodPayRemindServiceImpl service;

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		orderInfoService = mock(IOrderInfoService.class);
		orderConfigService = mock(IOrderConfigService.class);
		remoteMessageStaffService = mock(RemoteMessageStaffService.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		service = new CodPayRemindServiceImpl(orderInfoService, orderConfigService, rocketMQTemplate,
				new ObjectMapper());
		org.springframework.test.util.ReflectionTestUtils.setField(service, "remoteMessageStaffService",
				remoteMessageStaffService);
	}

	@Test
	void parseRemindHoursNullFallsBackToDefault() {
		assertThat(service.parseRemindHours(null)).containsExactly(72, 168);
	}

	@Test
	void parseRemindHoursEmptyDisables() {
		assertThat(service.parseRemindHours("")).isEmpty();
		assertThat(service.parseRemindHours("   ")).isEmpty();
	}

	@Test
	void parseRemindHoursDedupSortsAndIgnoresInvalid() {
		assertThat(service.parseRemindHours(" 168, 72 ,72,abc,-3,0 ")).containsExactly(72, 168);
	}

	@Test
	void skipsWhenConfigMissingOrDisabled() {
		when(orderConfigService.getConfig()).thenReturn(null);
		assertThat(service.remindTenant(TENANT_ID)).isZero();
		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));

		OrderConfig config = new OrderConfig();
		config.setCodPayRemindHours("");
		when(orderConfigService.getConfig()).thenReturn(config);
		assertThat(service.remindTenant(TENANT_ID)).isZero();
		verify(orderInfoService, never()).list(any(Wrapper.class));
	}

	@Test
	void remindsBuyerAndAdminsForFirstRound() {
		stubConfig("72,168");
		LocalDateTime now = LocalDateTime.now();
		OrderInfo order = codOrder("1001", now.minusHours(73));
		when(orderInfoService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of(staff("9001", "张管理")));

		int sent = service.remindTenant(TENANT_ID);

		assertThat(sent).isEqualTo(2);
		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate, times(2)).convertAndSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
				payloadCaptor.capture());
		List<Object> commands = payloadCaptor.getAllValues();
		assertThat(commands).allSatisfy(payload -> {
			MessageSendCommand command = (MessageSendCommand) payload;
			assertThat(command.getTenantId()).isEqualTo(TENANT_ID);
			assertThat(command.getCategory()).isEqualTo("ORDER");
			assertThat(command.getBizType()).isEqualTo("COD_PAY_REMIND");
			assertThat(command.getBizId()).isEqualTo("1001");
			assertThat(command.getChannels()).containsExactly("IN_APP");
		});
		MessageSendCommand buyer = (MessageSendCommand) commands.get(0);
		assertThat(buyer.getEventId()).isEqualTo("cod-pay-remind:buyer:1001:R1");
		assertThat(buyer.getRecipientType()).isEqualTo(MessageIdentityType.MALL_USER.name());
		assertThat(buyer.getRecipientId()).isEqualTo("user-1");
		assertThat(buyer.getTitle()).isEqualTo("货到付款订单待付款");
		assertThat(buyer.getContent()).contains("1800").contains("1001");
		assertThat(buyer.getJumpPayload()).contains("\"bizType\"").contains("\"ORDER\"").contains("1001");
		MessageSendCommand admin = (MessageSendCommand) commands.get(1);
		assertThat(admin.getEventId()).isEqualTo("cod-pay-remind:admin:1001:R1");
		assertThat(admin.getRecipientType()).isEqualTo(MessageIdentityType.SYS_USER.name());
		assertThat(admin.getRecipientId()).isEqualTo("9001");
		assertThat(admin.getRecipientName()).isEqualTo("张管理");
		assertThat(admin.getContent()).contains("第 1 轮");
	}

	@Test
	void catchesUpAllDueRounds() {
		stubConfig("72,168");
		LocalDateTime now = LocalDateTime.now();
		OrderInfo order = codOrder("1002", now.minusHours(200));
		when(orderInfoService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of(staff("9001", "张管理")));

		int sent = service.remindTenant(TENANT_ID);

		assertThat(sent).isEqualTo(4);
		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate, times(4)).convertAndSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
				payloadCaptor.capture());
		List<String> eventIds = payloadCaptor.getAllValues().stream()
			.map(payload -> ((MessageSendCommand) payload).getEventId())
			.toList();
		assertThat(eventIds).containsExactly("cod-pay-remind:buyer:1002:R1", "cod-pay-remind:admin:1002:R1",
				"cod-pay-remind:buyer:1002:R2", "cod-pay-remind:admin:1002:R2");
	}

	@Test
	void skipsOrdersBeforeFirstThreshold() {
		stubConfig("72,168");
		OrderInfo order = codOrder("1003", LocalDateTime.now().minusHours(71));
		when(orderInfoService.list(any(Wrapper.class))).thenReturn(List.of(order));

		assertThat(service.remindTenant(TENANT_ID)).isZero();
		verify(rocketMQTemplate, never()).convertAndSend(anyString(), any(Object.class));
	}

	@Test
	void stillRemindsBuyerWhenNoAdminFound() {
		stubConfig("72,168");
		OrderInfo order = codOrder("1004", LocalDateTime.now().minusHours(100));
		when(orderInfoService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of());

		assertThat(service.remindTenant(TENANT_ID)).isEqualTo(1);
		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(rocketMQTemplate, times(1)).convertAndSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC),
				payloadCaptor.capture());
		assertThat(((MessageSendCommand) payloadCaptor.getValue()).getRecipientType())
			.isEqualTo(MessageIdentityType.MALL_USER.name());
	}

	@Test
	void mqFailureDoesNotPropagate() {
		stubConfig("72,168");
		OrderInfo order = codOrder("1005", LocalDateTime.now().minusHours(100));
		when(orderInfoService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(remoteMessageStaffService.queryRecipientsByRoleCode(TENANT_ID, CommonConstants.ROLE_ADMIN_CODE))
			.thenReturn(List.of(staff("9001", "张管理")));
		doThrow(new RuntimeException("broker down")).when(rocketMQTemplate).convertAndSend(anyString(),
				any(Object.class));

		assertThat(service.remindTenant(TENANT_ID)).isZero();
	}

	private void stubConfig(String remindHours) {
		OrderConfig config = new OrderConfig();
		config.setCodPayRemindHours(remindHours);
		when(orderConfigService.getConfig()).thenReturn(config);
	}

	private OrderInfo codOrder(String id, LocalDateTime receiverTime) {
		return new OrderInfo().setId(id)
			.setUserId("user-1")
			.setOrderNo("COD" + id)
			.setPaymentType("3")
			.setPayStatus("0")
			.setStatus("4")
			.setPaymentPrice(new BigDecimal("1800"))
			.setTotalPrice(new BigDecimal("1800"))
			.setReceiverTime(receiverTime)
			.setRecipientName("李船长")
			.setTenantId(TENANT_ID);
	}

	private StaffMessageRecipientVO staff(String id, String nickname) {
		StaffMessageRecipientVO vo = new StaffMessageRecipientVO();
		vo.setId(id);
		vo.setNickname(nickname);
		return vo;
	}

}
