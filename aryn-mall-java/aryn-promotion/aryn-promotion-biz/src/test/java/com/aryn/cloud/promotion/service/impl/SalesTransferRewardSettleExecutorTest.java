package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.mapper.SalesTransferRewardMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 转交奖励单条释放执行器单测：独立事务内的锁定读、欠款抵消与余额不足跳过
 */
class SalesTransferRewardSettleExecutorTest {

	private SalesTransferRewardMapper mapper;

	private IDistributionUserService distributionUserService;

	private SalesTransferRewardSettleExecutor executor;

	@BeforeEach
	void setUp() {
		mapper = mock(SalesTransferRewardMapper.class);
		distributionUserService = mock(IDistributionUserService.class);
		executor = new SalesTransferRewardSettleExecutor(mapper, distributionUserService);
	}

	@Test
	void releasesPendingToAvailable() {
		SalesTransferReward reward = reward("reward-1", "distributor-1", "1.00");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setPendingCommission(new BigDecimal("5.00"))
			.setAvailableCommission(new BigDecimal("2.00"))
			.setTotalCommission(new BigDecimal("1.00"));
		when(distributionUserService.getOne(any())).thenReturn(distributor);
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(true);

		assertThat(executor.settleOne("reward-1")).isTrue();

		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("4.00");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("3.00");
		assertThat(distributor.getTotalCommission()).isEqualByComparingTo("2.00");
		ArgumentCaptor<SalesTransferReward> updateCaptor = ArgumentCaptor.forClass(SalesTransferReward.class);
		verify(mapper).update(updateCaptor.capture(), any());
		assertThat(updateCaptor.getValue().getStatus()).isEqualTo("1");
		assertThat(updateCaptor.getValue().getSettleTime()).isNotNull();
	}

	@Test
	void offsetsCommissionDebtBeforeCreditingAvailable() {
		SalesTransferReward reward = reward("reward-1", "distributor-1", "1.00");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setPendingCommission(new BigDecimal("5.00"))
			.setAvailableCommission(new BigDecimal("2.00"))
			.setCommissionDebt(new BigDecimal("0.30"))
			.setTotalCommission(new BigDecimal("1.00"));
		when(distributionUserService.getOne(any())).thenReturn(distributor);
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(true);

		assertThat(executor.settleOne("reward-1")).isTrue();

		// 先抵欠款 0.30：可提现只增加 0.70，欠款清零
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("4.00");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("2.70");
		assertThat(distributor.getCommissionDebt()).isEqualByComparingTo("0.00");
		assertThat(distributor.getTotalCommission()).isEqualByComparingTo("2.00");
	}

	@Test
	void skipsWhenPendingCommissionInsufficient() {
		SalesTransferReward reward = reward("reward-1", "distributor-1", "1.50");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setPendingCommission(new BigDecimal("0.20"))
			.setAvailableCommission(new BigDecimal("1.00"))
			.setTotalCommission(new BigDecimal("1.00"));
		when(distributionUserService.getOne(any())).thenReturn(distributor);

		assertThat(executor.settleOne("reward-1")).isFalse();

		// 保守跳过：不改余额、不改奖励状态，等待下次任务重试
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("0.20");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("1.00");
		assertThat(distributor.getTotalCommission()).isEqualByComparingTo("1.00");
		verify(mapper, never()).update(any(SalesTransferReward.class), any());
		verify(distributionUserService, never()).updateById(any());
	}

	@Test
	void skipsWhenRewardAlreadySettled() {
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward("reward-1", "distributor-1", "1.00")
			.setStatus("1"));

		assertThat(executor.settleOne("reward-1")).isFalse();

		verify(distributionUserService, never()).updateById(any());
	}

	@Test
	void skipsWhenRewardMissing() {
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);

		assertThat(executor.settleOne("reward-1")).isFalse();

		verify(distributionUserService, never()).updateById(any());
	}

	@Test
	void skipsWhenDistributorMissing() {
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward("reward-1", "distributor-1", "1.00"));
		when(distributionUserService.getOne(any())).thenReturn(null);

		assertThat(executor.settleOne("reward-1")).isFalse();

		verify(mapper, never()).update(any(SalesTransferReward.class), any());
		verify(distributionUserService, never()).updateById(any());
	}

	@Test
	void throwsWhenDistributorUpdateConflicts() {
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward("reward-1", "distributor-1", "1.00"));
		when(distributionUserService.getOne(any()))
			.thenReturn(new DistributionUser().setUserId("distributor-1")
				.setPendingCommission(new BigDecimal("1.00")));
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(false);

		assertThatThrownBy(() -> executor.settleOne("reward-1")).isInstanceOf(ArynBusinessException.class);
	}

	private SalesTransferReward reward(String id, String distributorUserId, String rewardAmount) {
		return new SalesTransferReward().setId(id)
			.setDistributorUserId(distributorUserId)
			.setRewardAmount(new BigDecimal(rewardAmount))
			.setStatus("0");
	}

}
