package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.BalanceRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.BalanceRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
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
 * BalanceRecordServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BalanceRecordServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@Mock
	private IMemberLevelService memberLevelService;

	@Mock
	private BalanceRecordMapper balanceRecordMapper;

	@InjectMocks
	private BalanceRecordServiceImpl balanceRecordService;

	private UserInfo testUser;

	@BeforeEach
	void setUp() {
		testUser = new UserInfo();
		testUser.setId("user001");
		testUser.setPoint(0);
		testUser.setBalance(new BigDecimal("100.00"));
		testUser.setTotalConsume(BigDecimal.ZERO);

		// 设置 baseMapper
		balanceRecordService.baseMapper = balanceRecordMapper;
	}

	@Test
	@DisplayName("充值 - 正常增加余额")
	void recordBalanceChange_recharge_success() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "1", new BigDecimal("50.00"), "RECHARGE", "充值");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(new BigDecimal("150.00")) == 0));
		verify(balanceRecordMapper).insert(argThat(record ->
				"user001".equals(record.getUserId())
						&& "1".equals(record.getChangeType())
						&& record.getChangeAmount().compareTo(new BigDecimal("50.00")) == 0
						&& record.getBalanceAfter().compareTo(new BigDecimal("150.00")) == 0
						&& "RECHARGE".equals(record.getTriggerScene())));
		// 充值触发等级重算
		verify(memberLevelService).recalculateLevel("user001");
	}

	@Test
	@DisplayName("消费 - 正常减少余额")
	void recordBalanceChange_consume_success() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "2", new BigDecimal("30.00"), "ORDER_PAY", "订单支付");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(new BigDecimal("70.00")) == 0));
		verify(balanceRecordMapper).insert(argThat(record ->
				"2".equals(record.getChangeType())
						&& record.getBalanceAfter().compareTo(new BigDecimal("70.00")) == 0));
		// 消费不触发等级重算
		verify(memberLevelService, never()).recalculateLevel(anyString());
	}

	@Test
	@DisplayName("消费 - 余额不足时抛出异常")
	void recordBalanceChange_consume_insufficientBalance() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				balanceRecordService.recordBalanceChange("user001", "2", new BigDecimal("200.00"), "ORDER_PAY", "订单支付"));

		assertEquals("余额不足", exception.getMsg());
		verify(userInfoMapper, never()).updateById(any());
		verify(balanceRecordMapper, never()).insert(any());
	}

	@Test
	@DisplayName("调整 - 正数调整增加余额")
	void recordBalanceChange_adjust_positiveAmount() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "3", new BigDecimal("20.00"), "ADMIN_ADJUST", "管理员调整");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(new BigDecimal("120.00")) == 0));
		verify(balanceRecordMapper).insert(argThat(record ->
				"3".equals(record.getChangeType())
						&& record.getBalanceAfter().compareTo(new BigDecimal("120.00")) == 0));
	}

	@Test
	@DisplayName("调整 - 负数调整减少余额")
	void recordBalanceChange_adjust_negativeAmount() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "3", new BigDecimal("-30.00"), "ADMIN_ADJUST", "管理员扣减");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(new BigDecimal("70.00")) == 0));
	}

	@Test
	@DisplayName("调整 - 调整后余额为负数时抛出异常")
	void recordBalanceChange_adjust_resultNegative() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				balanceRecordService.recordBalanceChange("user001", "3", new BigDecimal("-200.00"), "ADMIN_ADJUST", "管理员扣减"));

		assertEquals("调整后余额不能为负数", exception.getMsg());
		verify(userInfoMapper, never()).updateById(any());
	}

	@Test
	@DisplayName("用户不存在时抛出异常")
	void recordBalanceChange_userNotFound() {
		// given
		when(userInfoMapper.selectById("user999")).thenReturn(null);

		// when & then
		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () ->
				balanceRecordService.recordBalanceChange("user999", "1", new BigDecimal("50.00"), "RECHARGE", "充值"));

		assertEquals("用户不存在", exception.getMsg());
	}

	@Test
	@DisplayName("充值 - 用户余额为null时默认为ZERO")
	void recordBalanceChange_recharge_nullBalanceDefaultsToZero() {
		// given
		testUser.setBalance(null);
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "1", new BigDecimal("50.00"), "RECHARGE", "充值");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(new BigDecimal("50.00")) == 0));
	}

	@Test
	@DisplayName("消费 - 余额刚好等于消费金额时成功")
	void recordBalanceChange_consume_exactBalance() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);

		// when
		balanceRecordService.recordBalanceChange("user001", "2", new BigDecimal("100.00"), "ORDER_PAY", "订单支付");

		// then
		verify(userInfoMapper).updateById(argThat(user ->
				user.getBalance().compareTo(BigDecimal.ZERO) == 0));
	}

	@Test
	@DisplayName("充值 - 等级重算失败时不影响主流程")
	void recordBalanceChange_recharge_levelRecalculateFail_doesNotAffectMainFlow() {
		// given
		when(userInfoMapper.selectById("user001")).thenReturn(testUser);
		when(balanceRecordMapper.insert(any(BalanceRecord.class))).thenReturn(1);
		doThrow(new RuntimeException("等级服务异常")).when(memberLevelService).recalculateLevel("user001");

		// when & then
		assertDoesNotThrow(() ->
				balanceRecordService.recordBalanceChange("user001", "1", new BigDecimal("50.00"), "RECHARGE", "充值"));

		verify(userInfoMapper).updateById(any());
		verify(balanceRecordMapper).insert(any());
	}

}
