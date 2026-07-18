package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.BalanceRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.BalanceRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceRecordServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private BalanceRecordMapper balanceRecordMapper;

	private BalanceRecordServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new TestBalanceRecordService(userInfoMapper, balanceRecordMapper);
	}

	@Test
	void rechargeUsesAtomicIncrement() {
		when(userInfoMapper.selectById("user001")).thenReturn(user("100.00"), user("150.00"));
		when(userInfoMapper.changeBalance("user001", new BigDecimal("50.00"))).thenReturn(1);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		service.recordBalanceChange("user001", "1", new BigDecimal("50.00"), "RECHARGE", "充值");

		verify(userInfoMapper).changeBalance("user001", new BigDecimal("50.00"));
		verify(balanceRecordMapper).insert(org.mockito.ArgumentMatchers.<BalanceRecord>argThat(record ->
				record.getBalanceAfter().compareTo(new BigDecimal("150.00")) == 0));
	}

	@Test
	void consumeUsesNegativeAtomicDelta() {
		when(userInfoMapper.selectById("user001")).thenReturn(user("100.00"), user("70.00"));
		when(userInfoMapper.changeBalance("user001", new BigDecimal("-30.00"))).thenReturn(1);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		service.recordBalanceChange("user001", "2", new BigDecimal("30.00"), "ORDER_PAY", "订单支付");

		verify(userInfoMapper).changeBalance("user001", new BigDecimal("-30.00"));
	}

	@Test
	void guardedUpdateRejectsConcurrentInsufficientBalance() {
		when(userInfoMapper.selectById("user001")).thenReturn(user("100.00"));
		when(userInfoMapper.changeBalance("user001", new BigDecimal("-100.00"))).thenReturn(0);

		assertEquals("余额不足", assertThrows(ArynBusinessException.class,
				() -> service.recordBalanceChange("user001", "2", new BigDecimal("100.00"), "ORDER_PAY", "支付"))
			.getMsg());
		verify(balanceRecordMapper, never()).insert(any(BalanceRecord.class));
	}

	@Test
	void adjustmentAllowsSignedAmountButNeverNegativeResult() {
		when(userInfoMapper.selectById("user001")).thenReturn(user("100.00"), user("70.00"));
		when(userInfoMapper.changeBalance("user001", new BigDecimal("-30.00"))).thenReturn(1);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		service.recordBalanceChange("user001", "3", new BigDecimal("-30.00"), "ADMIN_ADJUST", "扣减");

		verify(userInfoMapper).changeBalance("user001", new BigDecimal("-30.00"));
	}

	@Test
	void rejectsUnknownTypeAndInvalidAmounts() {
		assertEquals("余额变动类型不合法", assertThrows(ArynBusinessException.class,
				() -> service.recordBalanceChange("user001", "4", BigDecimal.ONE, "MANUAL", "调整")).getMsg());
		assertEquals("余额变动值必须大于0", assertThrows(ArynBusinessException.class,
				() -> service.recordBalanceChange("user001", "1", BigDecimal.ZERO, "RECHARGE", "充值")).getMsg());
		assertEquals("余额调整值不能为0", assertThrows(ArynBusinessException.class,
				() -> service.recordBalanceChange("user001", "3", BigDecimal.ZERO, "MANUAL", "调整")).getMsg());
		verify(userInfoMapper, never()).selectById(any());
	}

	private UserInfo user(String balance) {
		return new UserInfo().setId("user001").setBalance(new BigDecimal(balance));
	}

	private static final class TestBalanceRecordService extends BalanceRecordServiceImpl {

		private TestBalanceRecordService(UserInfoMapper userInfoMapper, BalanceRecordMapper balanceRecordMapper) {
			super(userInfoMapper);
			this.baseMapper = balanceRecordMapper;
		}
	}

}
