package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.mapper.DistributionUserMapper;
import com.aryn.cloud.promotion.mapper.DistributionWithdrawMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionUserServiceImplTest {

	private DistributionWithdrawMapper withdrawMapper;
	private DistributionUserMapper userMapper;
	private DistributionUserServiceImpl service;

	@BeforeEach
	void setUp() {
		withdrawMapper = mock(DistributionWithdrawMapper.class);
		userMapper = mock(DistributionUserMapper.class);
		service = spy(new DistributionUserServiceImpl(withdrawMapper));
		ReflectionTestUtils.setField(service, "baseMapper", userMapper);
	}

	@Test
	void removeRejectsUserWithAnyUnsettledFinancialAmount() {
		DistributionUser user = user("dist-1", "user-1");
		user.setPendingCommission(new BigDecimal("0.01"));
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class);
		verify(service, never()).removeById(any(String.class));
	}

	@Test
	void removeRejectsUserWithCommissionDebt() {
		DistributionUser user = user("dist-1", "user-1");
		user.setCommissionDebt(new BigDecimal("0.01"));
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void removeRejectsUserWithPendingWithdraw() {
		DistributionUser user = user("dist-1", "user-1");
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);
		when(withdrawMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void removeAllowsUserWithoutFundsOrPendingWithdraw() {
		DistributionUser user = user("dist-1", "user-1");
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);
		doReturn(true).when(service).removeById("dist-1");
		when(withdrawMapper.selectCount(any())).thenReturn(0L);

		assertThat(service.removeSafely("dist-1")).isTrue();
		verify(service).removeById("dist-1");
	}

	@Test
	void registerCreatesNewActiveRecordWhenDeletedRecordIsNotVisible() {
		DistributionUserRegisterDTO dto = registerDto("user-1", null);
		doReturn(null).when(service).getByUserId("user-1");
		doReturn(true).when(service).save(any(DistributionUser.class));

		DistributionUser registered = service.register(dto);

		assertThat(registered.getUserId()).isEqualTo("user-1");
		assertThat(registered.getPendingCommission()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(registered.getCommissionDebt()).isEqualByComparingTo(BigDecimal.ZERO);
		verify(service).save(registered);
	}

	@Test
	void existingUserWithoutInviterCanBindInviterOnce() {
		DistributionUser existing = user("dist-1", "user-1");
		DistributionUser inviter = user("dist-2", "inviter-1");
		doReturn(existing).when(service).getByUserId("user-1");
		when(userMapper.selectByUserIdForUpdate("inviter-1")).thenReturn(inviter);
		doReturn(true).when(service).updateById(any(DistributionUser.class));

		DistributionUser result = service.register(registerDto("user-1", "inviter-1"));

		assertThat(result.getInviterUserId()).isEqualTo("inviter-1");
		assertThat(inviter.getSubordinateCount()).isEqualTo(1);
		verify(service).updateById(existing);
		verify(service).updateById(inviter);
	}

	@Test
	void removeLocksUserBeforeFinancialValidation() {
		DistributionUser user = user("dist-1", "user-1");
		user.setPendingCommission(new BigDecimal("0.01"));
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class);

		verify(userMapper).selectByIdForUpdate("dist-1");
	}

	@Test
	void removeRejectsUserWhoStillHasSubordinates() {
		DistributionUser user = user("dist-1", "user-1");
		user.setSubordinateCount(1);
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("下级");
	}

	@Test
	void removeRejectsUserWhenStoredCountDriftedButActiveChildExists() {
		DistributionUser user = user("dist-1", "user-1");
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);
		when(userMapper.selectCount(any())).thenReturn(1L);

		assertThatThrownBy(() -> service.removeSafely("dist-1"))
			.isInstanceOf(ArynBusinessException.class);

		verify(service, never()).removeById(any(String.class));
	}

	@Test
	void removingLeafDecrementsInviterCount() {
		DistributionUser user = user("dist-1", "user-1");
		user.setInviterUserId("inviter-1");
		DistributionUser inviter = user("dist-2", "inviter-1");
		inviter.setSubordinateCount(2);
		when(userMapper.selectByIdForUpdate("dist-1")).thenReturn(user);
		when(userMapper.selectByUserIdForUpdate("inviter-1")).thenReturn(inviter);
		when(withdrawMapper.selectCount(any())).thenReturn(0L);
		doReturn(true).when(service).removeById("dist-1");
		doReturn(true).when(service).updateById(inviter);

		assertThat(service.removeSafely("dist-1")).isTrue();

		assertThat(inviter.getSubordinateCount()).isEqualTo(1);
		verify(service).updateById(inviter);
	}

	@Test
	void bindingInviterRejectsIndirectCycle() {
		DistributionUser existing = user("dist-1", "user-1");
		DistributionUser inviter = user("dist-2", "user-2");
		inviter.setInviterUserId("user-3");
		DistributionUser ancestor = user("dist-3", "user-3");
		ancestor.setInviterUserId("user-1");
		doReturn(existing).when(service).getByUserId("user-1");
		when(userMapper.selectByUserIdForUpdate("user-2")).thenReturn(inviter);
		doReturn(ancestor).when(service).getByUserId("user-3");

		assertThatThrownBy(() -> service.register(registerDto("user-1", "user-2")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(throwable -> ((ArynBusinessException) throwable).getMsg())
			.asString()
			.contains("循环");
	}

	private DistributionUserRegisterDTO registerDto(String userId, String inviterUserId) {
		DistributionUserRegisterDTO dto = new DistributionUserRegisterDTO();
		dto.setUserId(userId);
		dto.setInviterUserId(inviterUserId);
		return dto;
	}

	private DistributionUser user(String id, String userId) {
		return new DistributionUser()
			.setId(id)
			.setUserId(userId)
			.setStatus("0")
			.setAvailableCommission(BigDecimal.ZERO)
			.setPendingCommission(BigDecimal.ZERO)
			.setFrozenCommission(BigDecimal.ZERO)
			.setCommissionDebt(BigDecimal.ZERO)
			.setSubordinateCount(0);
	}
}
