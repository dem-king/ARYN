package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.mapper.RechargeOrderMapper;
import com.aryn.cloud.user.service.IBalanceRecordService;
import com.aryn.cloud.user.service.IPointsRecordService;
import com.aryn.cloud.user.service.IRechargeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RechargeOrderServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RechargeOrderServiceImplTest {

	@Mock
	private IRechargeConfigService rechargeConfigService;

	@Mock
	private IBalanceRecordService balanceRecordService;

	@Mock
	private IPointsRecordService pointsRecordService;

	@Mock
	private RechargeOrderMapper rechargeOrderMapper;

	@InjectMocks
	private RechargeOrderServiceImpl rechargeOrderService;

	private RechargeConfig activeConfig;
	private RechargeConfig disabledConfig;

	@BeforeEach
	void setUp() {
		// 设置 baseMapper
		rechargeOrderService.baseMapper = rechargeOrderMapper;

		activeConfig = new RechargeConfig();
		activeConfig.setId("config001");
		activeConfig.setRechargeAmount(new BigDecimal("100.00"));
		activeConfig.setGiftAmount(new BigDecimal("10.00"));
		activeConfig.setGiftPoint(50);
		activeConfig.setStatus("0"); // 启用

		disabledConfig = new RechargeConfig();
		disabledConfig.setId("config002");
		disabledConfig.setRechargeAmount(new BigDecimal("200.00"));
		disabledConfig.setGiftAmount(BigDecimal.ZERO);
		disabledConfig.setGiftPoint(0);
		disabledConfig.setStatus("1"); // 禁用
	}

	@Test
	@DisplayName("创建订单 - 正常创建充值订单")
	void createOrder_success() {
		// given
		when(rechargeConfigService.getById("config001")).thenReturn(activeConfig);
		when(rechargeOrderMapper.insert(any(RechargeOrder.class))).thenReturn(1);

		// when
		RechargeOrder order = rechargeOrderService.createOrder("user001", "config001");

		// then
		assertNotNull(order);
		assertEquals("user001", order.getUserId());
		assertEquals("config001", order.getRechargeConfigId());
		assertEquals(0, order.getRechargeAmount().compareTo(new BigDecimal("100.00")));
		assertEquals(0, order.getGiftAmount().compareTo(new BigDecimal("10.00")));
		assertEquals(50, order.getGiftPoint());
		assertEquals("0", order.getPayStatus());
		assertNotNull(order.getOrderNo());
		// 订单号应为17位时间戳+6位随机数=23位
		assertTrue(order.getOrderNo().length() >= 19);
	}

	@Test
	@DisplayName("创建订单 - 充值配置不存在时抛出异常")
	void createOrder_configNotFound() {
		// given
		when(rechargeConfigService.getById("config999")).thenReturn(null);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.createOrder("user001", "config999"));

		assertEquals("充值配置不存在", exception.getMsg());
		verify(rechargeOrderMapper, never()).insert(any());
	}

	@Test
	@DisplayName("创建订单 - 充值配置已禁用时抛出异常")
	void createOrder_configDisabled() {
		// given
		when(rechargeConfigService.getById("config002")).thenReturn(disabledConfig);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.createOrder("user001", "config002"));

		assertEquals("充值配置已禁用", exception.getMsg());
		verify(rechargeOrderMapper, never()).insert(any());
	}

	@Test
	@DisplayName("支付成功 - 正常支付增加余额和赠送积分")
	void paySuccess_success() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setId("order001");
		order.setUserId("user001");
		order.setOrderNo("20240101120000000123456");
		order.setRechargeAmount(new BigDecimal("100.00"));
		order.setGiftAmount(new BigDecimal("10.00"));
		order.setGiftPoint(50);
		order.setPayStatus("0");

		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);
		when(rechargeOrderMapper.updateById(any(RechargeOrder.class))).thenReturn(1);

		// when
		rechargeOrderService.paySuccess("20240101120000000123456", "payOrder123");

		// then
		// 验证订单状态更新为已支付
		verify(rechargeOrderMapper).updateById(argThat(o ->
				"1".equals(o.getPayStatus())
						&& "payOrder123".equals(o.getPayOrderNo())
						&& o.getPayTime() != null));

		// 验证余额增加（充值金额+赠送金额）
		verify(balanceRecordService).recordBalanceChange(
				"user001", "1",
				new BigDecimal("110.00"),
				"RECHARGE",
				"充值订单：20240101120000000123456");

		// 验证积分增加
		verify(pointsRecordService).recordPointsChange(
				"user001", "1", 50, "RECHARGE",
				"充值赠送积分：20240101120000000123456");
	}

	@Test
	@DisplayName("支付成功 - 订单不存在时抛出异常")
	void paySuccess_orderNotFound() {
		// given
		when(rechargeOrderMapper.selectOne(any())).thenReturn(null);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.paySuccess("nonexistent", "payOrder123"));

		assertEquals("订单不存在", exception.getMsg());
		verify(balanceRecordService, never()).recordBalanceChange(anyString(), anyString(), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("支付成功 - 订单状态异常(非待支付)时抛出异常")
	void paySuccess_invalidOrderStatus() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setPayStatus("1"); // 已支付
		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.paySuccess("orderNo123", "payOrder123"));

		assertEquals("订单状态异常", exception.getMsg());
	}

	@Test
	@DisplayName("支付成功 - 无赠送积分时不发放积分")
	void paySuccess_noGiftPoint() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setId("order001");
		order.setUserId("user001");
		order.setOrderNo("orderNo001");
		order.setRechargeAmount(new BigDecimal("100.00"));
		order.setGiftAmount(BigDecimal.ZERO);
		order.setGiftPoint(0); // 无赠送积分
		order.setPayStatus("0");

		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);
		when(rechargeOrderMapper.updateById(any(RechargeOrder.class))).thenReturn(1);

		// when
		rechargeOrderService.paySuccess("orderNo001", "payOrder123");

		// then
		verify(balanceRecordService).recordBalanceChange(eq("user001"), eq("1"), any(), eq("RECHARGE"), anyString());
		verify(pointsRecordService, never()).recordPointsChange(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("支付成功 - 赠送积分为null时不发放积分")
	void paySuccess_giftPointNull() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setId("order001");
		order.setUserId("user001");
		order.setOrderNo("orderNo001");
		order.setRechargeAmount(new BigDecimal("100.00"));
		order.setGiftAmount(BigDecimal.ZERO);
		order.setGiftPoint(null); // 赠送积分为null
		order.setPayStatus("0");

		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);
		when(rechargeOrderMapper.updateById(any(RechargeOrder.class))).thenReturn(1);

		// when
		rechargeOrderService.paySuccess("orderNo001", "payOrder123");

		// then
		verify(pointsRecordService, never()).recordPointsChange(anyString(), anyString(), anyInt(), anyString(), anyString());
	}

	@Test
	@DisplayName("取消订单 - 正常取消待支付订单")
	void cancelOrder_success() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setId("order001");
		order.setOrderNo("orderNo001");
		order.setPayStatus("0"); // 待支付

		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);
		when(rechargeOrderMapper.updateById(any(RechargeOrder.class))).thenReturn(1);

		// when
		rechargeOrderService.cancelOrder("orderNo001");

		// then
		verify(rechargeOrderMapper).updateById(argThat(o -> "2".equals(o.getPayStatus())));
	}

	@Test
	@DisplayName("取消订单 - 订单不存在时抛出异常")
	void cancelOrder_orderNotFound() {
		// given
		when(rechargeOrderMapper.selectOne(any())).thenReturn(null);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.cancelOrder("nonexistent"));

		assertEquals("订单不存在", exception.getMsg());
	}

	@Test
	@DisplayName("取消订单 - 非待支付订单不能取消")
	void cancelOrder_notPendingPayment() {
		// given
		RechargeOrder order = new RechargeOrder();
		order.setPayStatus("1"); // 已支付

		when(rechargeOrderMapper.selectOne(any())).thenReturn(order);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				rechargeOrderService.cancelOrder("orderNo001"));

		assertEquals("只能取消待支付订单", exception.getMsg());
		verify(rechargeOrderMapper, never()).updateById(any());
	}

}
