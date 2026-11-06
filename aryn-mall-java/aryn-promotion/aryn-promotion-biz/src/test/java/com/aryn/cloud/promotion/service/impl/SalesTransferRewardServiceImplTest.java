package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.SharedString;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.mapper.SalesTransferRewardMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 转交奖励幂等、待结算释放（锁定读 + 欠款抵消）与全额退款扣回单测
 */
class SalesTransferRewardServiceImplTest {

	private SalesTransferRewardMapper mapper;

	private IDistributionUserService distributionUserService;

	private IDistributionCommissionFlowService distributionCommissionFlowService;

	private IDistributionConfigService distributionConfigService;

	private SalesTransferRewardSettleExecutor salesTransferRewardSettleExecutor;

	private SalesTransferRewardServiceImpl service;

	@BeforeEach
	void setUp() {
		mapper = mock(SalesTransferRewardMapper.class);
		distributionUserService = mock(IDistributionUserService.class);
		distributionCommissionFlowService = mock(IDistributionCommissionFlowService.class);
		distributionConfigService = mock(IDistributionConfigService.class);
		salesTransferRewardSettleExecutor = mock(SalesTransferRewardSettleExecutor.class);
		service = new SalesTransferRewardServiceImpl(distributionUserService, distributionCommissionFlowService,
				distributionConfigService, mock(SalesUserEnricher.class), salesTransferRewardSettleExecutor);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
	}

	@Test
	void grantsRewardIntoPendingCommissionWaitingForSettleCycle() {
		when(mapper.selectCount(any())).thenReturn(0L);
		when(mapper.insert(any(SalesTransferReward.class))).thenReturn(1);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setNickname("张三")
			.setPendingCommission(new BigDecimal("1.00"))
			.setAvailableCommission(new BigDecimal("2.00"));
		when(distributionUserService.getByUserId("distributor-1")).thenReturn(distributor);
		when(distributionUserService.updateById(any())).thenReturn(true);
		when(distributionCommissionFlowService.save(any())).thenReturn(true);

		boolean granted = service.grantReward("buyer-1", "distributor-1", "staff-1", "order-1",
				new BigDecimal("100.00"), new BigDecimal("0.0100"));

		assertThat(granted).isTrue();
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("2.00");
		ArgumentCaptor<SalesTransferReward> rewardCaptor = ArgumentCaptor.forClass(SalesTransferReward.class);
		verify(mapper).insert(rewardCaptor.capture());
		SalesTransferReward reward = rewardCaptor.getValue();
		assertThat(reward.getRewardAmount()).isEqualByComparingTo("1.00");
		assertThat(reward.getRewardRate()).isEqualByComparingTo("0.0100");
		// 奖励先停在待结算，由结算周期任务释放
		assertThat(reward.getStatus()).isEqualTo("0");
		assertThat(reward.getSettleTime()).isNull();
		assertThat(reward.getFirstOrderAmount()).isEqualByComparingTo("100.00");
		ArgumentCaptor<DistributionCommissionFlow> flowCaptor = ArgumentCaptor
			.forClass(DistributionCommissionFlow.class);
		verify(distributionCommissionFlowService).save(flowCaptor.capture());
		assertThat(flowCaptor.getValue().getFlowType()).isEqualTo("INCOME");
		assertThat(flowCaptor.getValue().getAmount()).isEqualByComparingTo("1.00");
		assertThat(flowCaptor.getValue().getRemark()).isEqualTo("客户转交奖励");
	}

	@Test
	void duplicatePaymentEventDoesNotCreditTwice() {
		when(mapper.selectCount(any())).thenReturn(1L);

		boolean granted = service.grantReward("buyer-1", "distributor-1", "staff-1", "order-1",
				new BigDecimal("100.00"), new BigDecimal("0.0100"));

		assertThat(granted).isFalse();
		verify(mapper, never()).insert(any(SalesTransferReward.class));
		verify(distributionUserService, never()).updateById(any());
	}

	@Test
	void zeroRewardRateIsNotEnabled() {
		boolean granted = service.grantReward("buyer-1", "distributor-1", "staff-1", "order-1",
				new BigDecimal("100.00"), BigDecimal.ZERO);

		assertThat(granted).isFalse();
		verify(mapper, never()).insert(any(SalesTransferReward.class));
	}

	@Test
	void rewardAmountRoundsDownToTwoDecimals() {
		when(mapper.selectCount(any())).thenReturn(0L);
		when(mapper.insert(any(SalesTransferReward.class))).thenReturn(1);
		when(distributionUserService.getByUserId("distributor-1")).thenReturn(new DistributionUser()
			.setUserId("distributor-1")
			.setPendingCommission(BigDecimal.ZERO));
		when(distributionUserService.updateById(any())).thenReturn(true);
		when(distributionCommissionFlowService.save(any())).thenReturn(true);

		service.grantReward("buyer-1", "distributor-1", "staff-1", "order-1", new BigDecimal("99.99"),
				new BigDecimal("0.0100"));

		ArgumentCaptor<SalesTransferReward> rewardCaptor = ArgumentCaptor.forClass(SalesTransferReward.class);
		verify(mapper).insert(rewardCaptor.capture());
		// 99.99 * 0.01 = 0.9999 向下取整 0.99
		assertThat(rewardCaptor.getValue().getRewardAmount()).isEqualByComparingTo("0.99");
	}

	@Test
	void settlePendingRewardsDelegatesEachRewardToIndependentTransaction() {
		when(distributionConfigService.getActiveConfig())
			.thenReturn(new DistributionConfig().setSettleCycleDays(7));
		SalesTransferReward first = pendingReward("reward-1", "distributor-1", "1.00");
		SalesTransferReward second = pendingReward("reward-2", "distributor-2", "2.00");
		when(mapper.selectList(any())).thenReturn(List.of(first, second));
		when(salesTransferRewardSettleExecutor.settleOne("reward-1")).thenReturn(true);
		when(salesTransferRewardSettleExecutor.settleOne("reward-2")).thenReturn(true);

		int settled = service.settlePendingRewards();

		assertThat(settled).isEqualTo(2);
		// 每条奖励由独立事务执行器释放（批量方法本身不再开启整批事务）
		verify(salesTransferRewardSettleExecutor).settleOne("reward-1");
		verify(salesTransferRewardSettleExecutor).settleOne("reward-2");
	}

	@Test
	void settlePendingRewardsContinuesWhenOneRewardFails() {
		when(distributionConfigService.getActiveConfig()).thenReturn(null);
		when(mapper.selectList(any())).thenReturn(List.of(
			pendingReward("reward-1", "distributor-1", "1.00"),
			pendingReward("reward-2", "distributor-1", "2.00"),
			pendingReward("reward-3", "distributor-2", "3.00")));
		when(salesTransferRewardSettleExecutor.settleOne("reward-1"))
			.thenThrow(new IllegalStateException("poison reward"));
		when(salesTransferRewardSettleExecutor.settleOne("reward-2")).thenReturn(false);
		when(salesTransferRewardSettleExecutor.settleOne("reward-3")).thenReturn(true);

		assertThat(service.settlePendingRewards()).isEqualTo(1);

		// 单条抛异常不影响后续奖励继续释放
		verify(salesTransferRewardSettleExecutor).settleOne("reward-1");
		verify(salesTransferRewardSettleExecutor).settleOne("reward-2");
		verify(salesTransferRewardSettleExecutor).settleOne("reward-3");
	}

	@Test
	void settlePendingRewardsWithoutPendingRewardDoesNothing() {
		when(distributionConfigService.getActiveConfig()).thenReturn(null);
		when(mapper.selectList(any())).thenReturn(List.of());

		assertThat(service.settlePendingRewards()).isZero();
		verify(salesTransferRewardSettleExecutor, never()).settleOne(any());
	}

	@Test
	void clawbackPendingRewardDeductsPendingAndWritesExpenseFlow() {
		SalesTransferReward reward = pendingReward("reward-1", "distributor-1", "1.00");
		reward.setStatus("0");
		reward.setBizOrderId("order-1");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setPendingCommission(new BigDecimal("2.00"))
			.setAvailableCommission(new BigDecimal("0.50"))
			.setCommissionDebt(BigDecimal.ZERO);
		when(distributionUserService.getOne(any())).thenReturn(distributor);
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(true);
		when(distributionCommissionFlowService.save(any())).thenReturn(true);

		boolean clawedBack = service.clawbackReward("order-1");

		assertThat(clawedBack).isTrue();
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("1.00");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("0.50");
		ArgumentCaptor<DistributionCommissionFlow> flowCaptor = ArgumentCaptor
			.forClass(DistributionCommissionFlow.class);
		verify(distributionCommissionFlowService).save(flowCaptor.capture());
		DistributionCommissionFlow flow = flowCaptor.getValue();
		assertThat(flow.getFlowType()).isEqualTo("EXPENSE");
		assertThat(flow.getBizOrderId()).isEqualTo("order-1");
		assertThat(flow.getAmount()).isEqualByComparingTo("1.00");
		assertThat(flow.getBalanceAfter()).isEqualByComparingTo("0.50");
		assertThat(flow.getRemark()).isEqualTo("首单全额退款扣回转交奖励");
	}

	@Test
	void clawbackReleasedRewardDeductsAvailableAndTotalCommission() {
		SalesTransferReward reward = pendingReward("reward-1", "distributor-1", "1.00");
		reward.setStatus("1");
		reward.setBizOrderId("order-1");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setPendingCommission(BigDecimal.ZERO)
			.setAvailableCommission(new BigDecimal("0.60"))
			.setTotalCommission(new BigDecimal("5.00"));
		when(distributionUserService.getOne(any())).thenReturn(distributor);
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(true);
		when(distributionCommissionFlowService.save(any())).thenReturn(true);

		boolean clawedBack = service.clawbackReward("order-1");

		assertThat(clawedBack).isTrue();
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("0.00");
		assertThat(distributor.getCommissionDebt()).isEqualByComparingTo("0.40");
		assertThat(distributor.getTotalCommission()).isEqualByComparingTo("4.00");
		ArgumentCaptor<DistributionCommissionFlow> flowCaptor = ArgumentCaptor
			.forClass(DistributionCommissionFlow.class);
		verify(distributionCommissionFlowService).save(flowCaptor.capture());
		assertThat(flowCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("0.00");
	}

	@Test
	@SuppressWarnings("unchecked")
	void clawbackReadsRewardAndDistributorForUpdate() {
		SalesTransferReward reward = pendingReward("reward-1", "distributor-1", "1.00");
		reward.setStatus("1");
		reward.setBizOrderId("order-1");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1")
			.setAvailableCommission(new BigDecimal("1.00"))
			.setTotalCommission(new BigDecimal("1.00"));
		when(distributionUserService.getOne(any())).thenReturn(distributor);
		when(mapper.update(any(SalesTransferReward.class), any())).thenReturn(1);
		when(distributionUserService.updateById(any())).thenReturn(true);
		when(distributionCommissionFlowService.save(any())).thenReturn(true);

		assertThat(service.clawbackReward("order-1")).isTrue();

		// FOR UPDATE 当前读：确保能看到并等待结算任务已提交的 status/余额变更（RR 快照读会读到旧值）
		ArgumentCaptor<Wrapper> rewardCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper, times(1)).selectOne(rewardCaptor.capture(), anyBoolean());
		assertThat(lastSqlOf(rewardCaptor.getValue())).contains("FOR UPDATE");
		ArgumentCaptor<Wrapper> distributorCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(distributionUserService, times(1)).getOne(distributorCaptor.capture());
		assertThat(lastSqlOf(distributorCaptor.getValue())).contains("FOR UPDATE");
	}

	private String lastSqlOf(Wrapper<?> wrapper) {
		SharedString lastSql = (SharedString) ReflectionTestUtils.getField(wrapper, "lastSql");
		return lastSql == null ? null : lastSql.getStringValue();
	}

	@Test
	void clawbackIgnoresInvalidatedReward() {
		SalesTransferReward reward = pendingReward("reward-1", "distributor-1", "1.00").setStatus("2")
			.setBizOrderId("order-1");
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(reward);

		assertThat(service.clawbackReward("order-1")).isFalse();

		verify(mapper, never()).update(any(SalesTransferReward.class), any());
		verify(distributionUserService, never()).updateById(any());
		verify(distributionCommissionFlowService, never()).save(any());
	}

	@Test
	void clawbackWithoutRewardReturnsFalse() {
		when(mapper.selectOne(any(), anyBoolean())).thenReturn(null);

		assertThat(service.clawbackReward("order-1")).isFalse();

		verify(distributionUserService, never()).updateById(any());
		verify(distributionCommissionFlowService, never()).save(any());
	}

	private SalesTransferReward pendingReward(String id, String distributorUserId, String rewardAmount) {
		return new SalesTransferReward().setId(id)
			.setDistributorUserId(distributorUserId)
			.setRewardAmount(new BigDecimal(rewardAmount))
			.setStatus("0");
	}

}
