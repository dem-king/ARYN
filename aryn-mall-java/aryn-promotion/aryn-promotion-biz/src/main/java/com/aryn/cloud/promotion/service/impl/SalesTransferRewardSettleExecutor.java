package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.enums.SalesTransferRewardStatusEnum;
import com.aryn.cloud.promotion.mapper.SalesTransferRewardMapper;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 转交奖励单条释放执行器
 * <p>
 * 每条奖励在独立事务（REQUIRES_NEW）内按「先锁 reward 行、后锁 distributor 行」完成释放。
 * 拆成独立短事务后，distributor 行锁不会持有到整批结束，避免与 {@code clawbackReward}
 * 形成「R2↔D」的批量死锁；单条失败只回滚该条，不影响其他奖励释放。
 * <p>
 * 不反向依赖 {@code ISalesTransferRewardService}，避免循环依赖。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTransferRewardSettleExecutor {

	private final SalesTransferRewardMapper salesTransferRewardMapper;

	private final IDistributionUserService distributionUserService;

	/**
	 * 在独立事务中释放单条转交奖励：锁定读奖励与分销员，先抵欠款再把待结算佣金转为可提现佣金。
	 * @param rewardId 转交奖励ID
	 * @return true=本次完成释放，false=已处理/余额不足/分销员缺失等可跳过场景
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public boolean settleOne(String rewardId) {
		if (rewardId == null || rewardId.isBlank()) {
			return false;
		}
		// 锁定读奖励行：锁定后按最新状态判断，避免与 clawbackReward 并发时重复释放
		SalesTransferReward reward = salesTransferRewardMapper
			.selectOne(Wrappers.<SalesTransferReward>lambdaQuery()
				.eq(SalesTransferReward::getId, rewardId)
				.last("limit 1 FOR UPDATE"), true);
		if (reward == null || !SalesTransferRewardStatusEnum.STATUS_0.getCode().equals(reward.getStatus())) {
			log.info("转交奖励已被其他任务处理，跳过 rewardId={}", rewardId);
			return false;
		}
		BigDecimal rewardAmount = defaultZero(reward.getRewardAmount()).max(BigDecimal.ZERO);
		// 锁定读分销员余额（锁顺序：先 reward 后 distributor，与 clawbackReward 一致，避免死锁）
		DistributionUser distributor = distributionUserService
			.getOne(Wrappers.<DistributionUser>lambdaQuery()
				.eq(DistributionUser::getUserId, reward.getDistributorUserId())
				.last("limit 1 FOR UPDATE"));
		if (distributor == null) {
			log.warn("原分销员不存在，转交奖励无法释放 rewardId={}, distributorUserId={}", rewardId,
					reward.getDistributorUserId());
			return false;
		}
		BigDecimal pending = defaultZero(distributor.getPendingCommission());
		if (pending.compareTo(rewardAmount) < 0) {
			// 待结算佣金不足（可能被并发退款扣减）：保守跳过，不动余额/状态，等待下次任务重试
			log.warn("待结算佣金不足，暂不释放转交奖励 rewardId={}, distributorUserId={}, pending={}, rewardAmount={}",
					rewardId, reward.getDistributorUserId(), pending, rewardAmount);
			return false;
		}
		SalesTransferReward update = new SalesTransferReward();
		update.setStatus(SalesTransferRewardStatusEnum.STATUS_1.getCode());
		update.setSettleTime(LocalDateTime.now());
		int rows = salesTransferRewardMapper.update(update, Wrappers.<SalesTransferReward>lambdaUpdate()
			.eq(SalesTransferReward::getId, reward.getId())
			.eq(SalesTransferReward::getStatus, SalesTransferRewardStatusEnum.STATUS_0.getCode()));
		if (rows <= 0) {
			log.info("转交奖励已被其他任务释放，跳过 rewardId={}", rewardId);
			return false;
		}
		// 按分销结算口径先抵欠款，再把剩余部分转为可提现佣金
		BigDecimal debt = defaultZero(distributor.getCommissionDebt());
		BigDecimal debtOffset = rewardAmount.min(debt);
		distributor.setPendingCommission(pending.subtract(rewardAmount));
		distributor.setAvailableCommission(defaultZero(distributor.getAvailableCommission())
			.add(rewardAmount.subtract(debtOffset)));
		distributor.setCommissionDebt(debt.subtract(debtOffset));
		distributor.setTotalCommission(defaultZero(distributor.getTotalCommission()).add(rewardAmount));
		if (!distributionUserService.updateById(distributor)) {
			throw new ArynBusinessException("转交奖励释放更新佣金冲突，请重试");
		}
		log.info("转交奖励释放到可提现佣金 rewardId={}, distributorUserId={}, rewardAmount={}, debtOffset={}", rewardId,
				reward.getDistributorUserId(), rewardAmount, debtOffset);
		return true;
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
