package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesTransferReward;
import com.aryn.cloud.promotion.api.enums.CommissionFlowTypeEnum;
import com.aryn.cloud.promotion.api.enums.SalesTransferRewardStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesTransferRewardVO;
import com.aryn.cloud.promotion.mapper.SalesTransferRewardMapper;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.ISalesTransferRewardService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 客户转交奖励服务实现
 * <p>
 * 奖励生成时进入原分销员<b>待结算佣金</b>，由分销结算周期任务按 {@code settle_cycle_days} 释放到可提现佣金；
 * 客户首单全额退款时按 {@code bizOrderId} 扣回（pending/available/commissionDebt 三态处理）。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesTransferRewardServiceImpl extends ServiceImpl<SalesTransferRewardMapper, SalesTransferReward>
	implements ISalesTransferRewardService {

	/** 分销配置缺失时的默认结算周期（天），与分销订单释放口径一致 */
	private static final int DEFAULT_SETTLE_CYCLE_DAYS = 7;

	private final IDistributionUserService distributionUserService;

	private final IDistributionCommissionFlowService distributionCommissionFlowService;

	private final IDistributionConfigService distributionConfigService;

	private final SalesUserEnricher salesUserEnricher;

	private final SalesTransferRewardSettleExecutor salesTransferRewardSettleExecutor;

	@Override
	public IPage<SalesTransferRewardVO> selectAdminPage(Page<SalesTransferReward> page, SalesTransferReward query) {
		SalesTransferReward condition = query == null ? new SalesTransferReward() : query;
		IPage<SalesTransferReward> rewardPage = this.page(page, Wrappers.<SalesTransferReward>lambdaQuery()
			.eq(notBlank(condition.getCustomerUserId()), SalesTransferReward::getCustomerUserId,
					condition.getCustomerUserId())
			.eq(notBlank(condition.getSalesStaffId()), SalesTransferReward::getSalesStaffId,
					condition.getSalesStaffId())
			.eq(notBlank(condition.getStatus()), SalesTransferReward::getStatus, condition.getStatus())
			.orderByDesc(SalesTransferReward::getCreateTime));
		return convertWithNickname(rewardPage);
	}

	@Override
	public IPage<SalesTransferRewardVO> selectAppPage(Page<SalesTransferReward> page, String distributorUserId) {
		IPage<SalesTransferReward> rewardPage = this.page(page, Wrappers.<SalesTransferReward>lambdaQuery()
			.eq(SalesTransferReward::getDistributorUserId, distributorUserId)
			.orderByDesc(SalesTransferReward::getCreateTime));
		return convertWithNickname(rewardPage);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean grantReward(String customerUserId, String distributorUserId, String salesStaffId, String bizOrderId,
			BigDecimal firstOrderAmount, BigDecimal rewardRate) {
		if (customerUserId == null || customerUserId.isBlank() || distributorUserId == null
				|| distributorUserId.isBlank() || bizOrderId == null || bizOrderId.isBlank() || rewardRate == null
				|| rewardRate.signum() <= 0 || firstOrderAmount == null) {
			return false;
		}
		BigDecimal rewardAmount = firstOrderAmount.max(BigDecimal.ZERO)
			.multiply(rewardRate)
			.setScale(2, RoundingMode.DOWN);
		if (rewardAmount.signum() <= 0) {
			log.info("转交奖励金额为0，跳过 customerUserId={}, bizOrderId={}", customerUserId, bizOrderId);
			return false;
		}
		Long exists = this.baseMapper.selectCount(Wrappers.<SalesTransferReward>lambdaQuery()
			.eq(SalesTransferReward::getCustomerUserId, customerUserId)
			.eq(SalesTransferReward::getBizOrderId, bizOrderId));
		if (exists != null && exists > 0) {
			log.info("转交奖励已存在，跳过 customerUserId={}, bizOrderId={}", customerUserId, bizOrderId);
			return false;
		}
		DistributionUser distributor = distributionUserService.getByUserId(distributorUserId);
		if (distributor == null) {
			log.warn("原分销员不存在，转交奖励无法入账 distributorUserId={}", distributorUserId);
			return false;
		}

		SalesTransferReward reward = new SalesTransferReward();
		reward.setCustomerUserId(customerUserId);
		reward.setDistributorUserId(distributorUserId);
		reward.setSalesStaffId(salesStaffId);
		reward.setBizOrderId(bizOrderId);
		reward.setFirstOrderAmount(firstOrderAmount.setScale(2, RoundingMode.DOWN));
		reward.setRewardRate(rewardRate);
		reward.setRewardAmount(rewardAmount);
		// 奖励先进入待结算佣金，由分销结算周期任务释放到可提现佣金
		reward.setStatus(SalesTransferRewardStatusEnum.STATUS_0.getCode());
		reward.setSettleTime(null);
		try {
			if (!this.save(reward)) {
				throw new ArynBusinessException("转交奖励保存失败");
			}
		}
		catch (DuplicateKeyException e) {
			log.info("并发重复支付事件，转交奖励已存在 customerUserId={}, bizOrderId={}", customerUserId, bizOrderId);
			return false;
		}

		// 奖励入账到原分销员待结算佣金
		BigDecimal pending = distributor.getPendingCommission() == null
			? BigDecimal.ZERO : distributor.getPendingCommission();
		distributor.setPendingCommission(pending.add(rewardAmount));
		if (!distributionUserService.updateById(distributor)) {
			throw new ArynBusinessException("转交奖励入账失败，请重试");
		}

		DistributionCommissionFlow flow = new DistributionCommissionFlow();
		flow.setUserId(distributorUserId);
		flow.setBizOrderId(bizOrderId);
		flow.setFlowType(CommissionFlowTypeEnum.INCOME.getCode());
		flow.setAmount(rewardAmount);
		flow.setBalanceAfter(distributor.getAvailableCommission() == null
			? BigDecimal.ZERO : distributor.getAvailableCommission());
		flow.setRemark("客户转交奖励");
		if (!distributionCommissionFlowService.save(flow)) {
			throw new ArynBusinessException("转交奖励佣金流水保存失败");
		}
		log.info("客户转交奖励入账待结算 customerUserId={}, distributorUserId={}, bizOrderId={}, rewardAmount={}",
				customerUserId, distributorUserId, bizOrderId, rewardAmount);
		return true;
	}

	@Override
	public int settlePendingRewards() {
		DistributionConfig config = distributionConfigService.getActiveConfig();
		int cycleDays = config == null || config.getSettleCycleDays() == null
			? DEFAULT_SETTLE_CYCLE_DAYS : Math.max(config.getSettleCycleDays(), 0);
		LocalDateTime deadline = LocalDateTime.now().minusDays(cycleDays);
		List<SalesTransferReward> pendingRewards = this.list(Wrappers.<SalesTransferReward>lambdaQuery()
			.eq(SalesTransferReward::getStatus, SalesTransferRewardStatusEnum.STATUS_0.getCode())
			.le(SalesTransferReward::getCreateTime, deadline)
			.orderByAsc(SalesTransferReward::getId));
		if (pendingRewards.isEmpty()) {
			return 0;
		}
		int settled = 0;
		for (SalesTransferReward reward : pendingRewards) {
			try {
				// 每条奖励独立新事务释放：短事务 + 尽早释放 distributor 行锁，避免批量死锁
				if (salesTransferRewardSettleExecutor.settleOne(reward.getId())) {
					settled++;
				}
			}
			catch (Exception e) {
				log.warn("转交奖励释放失败，跳过等待下次重试 rewardId={}, error={}", reward.getId(), e.getMessage());
			}
		}
		if (settled > 0) {
			log.info("转交奖励待结算释放完成 settled={}, cycleDays={}", settled, cycleDays);
		}
		return settled;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean clawbackReward(String bizOrderId) {
		if (bizOrderId == null || bizOrderId.isBlank()) {
			return false;
		}
		// 锁定读奖励行：FOR UPDATE 为当前读，可看到并等待并发结算任务已提交的 status 变更
		// （MySQL 默认 REPEATABLE READ 下普通 getOne 是快照读，重读会一直看到旧状态）
		SalesTransferReward reward = this.getOne(Wrappers.<SalesTransferReward>lambdaQuery()
			.eq(SalesTransferReward::getBizOrderId, bizOrderId)
			.last("limit 1 FOR UPDATE"));
		if (reward == null) {
			log.info("无转交奖励，跳过扣回 bizOrderId={}", bizOrderId);
			return false;
		}
		String originStatus = reward.getStatus();
		if (!SalesTransferRewardStatusEnum.STATUS_0.getCode().equals(originStatus)
				&& !SalesTransferRewardStatusEnum.STATUS_1.getCode().equals(originStatus)) {
			log.info("转交奖励已扣回或已作废，跳过 bizOrderId={}, status={}", bizOrderId, originStatus);
			return false;
		}
		// 锁定读分销员余额（锁顺序：先 reward 后 distributor，与 settlePendingReward 一致，避免死锁）
		DistributionUser distributor = distributionUserService
			.getOne(Wrappers.<DistributionUser>lambdaQuery()
				.eq(DistributionUser::getUserId, reward.getDistributorUserId())
				.last("limit 1 FOR UPDATE"));
		if (distributor == null) {
			log.warn("原分销员不存在，转交奖励无法扣回 bizOrderId={}, distributorUserId={}", bizOrderId,
					reward.getDistributorUserId());
			return false;
		}
		SalesTransferReward update = new SalesTransferReward();
		update.setStatus(SalesTransferRewardStatusEnum.STATUS_2.getCode());
		int rows = this.baseMapper.update(update, Wrappers.<SalesTransferReward>lambdaUpdate()
			.eq(SalesTransferReward::getId, reward.getId())
			.eq(SalesTransferReward::getStatus, originStatus));
		if (rows <= 0) {
			log.info("转交奖励状态已变更，跳过扣回 bizOrderId={}", bizOrderId);
			return false;
		}
		BigDecimal rewardAmount = defaultZero(reward.getRewardAmount()).max(BigDecimal.ZERO);
		BigDecimal available = defaultZero(distributor.getAvailableCommission());
		if (SalesTransferRewardStatusEnum.STATUS_0.getCode().equals(originStatus)) {
			// 尚未释放：先扣待结算佣金，不足部分再扣可提现佣金，仍不足记欠款
			BigDecimal pending = defaultZero(distributor.getPendingCommission());
			BigDecimal pendingDeduct = pending.min(rewardAmount);
			BigDecimal shortfall = rewardAmount.subtract(pendingDeduct);
			BigDecimal availableDeduct = available.min(shortfall);
			BigDecimal debt = shortfall.subtract(availableDeduct);
			distributor.setPendingCommission(pending.subtract(pendingDeduct));
			distributor.setAvailableCommission(available.subtract(availableDeduct));
			if (debt.signum() > 0) {
				distributor.setCommissionDebt(defaultZero(distributor.getCommissionDebt()).add(debt));
				log.warn("转交奖励扣回时佣金余额不足，差额记欠款 bizOrderId={}, distributorUserId={}, debt={}",
						bizOrderId, reward.getDistributorUserId(), debt);
			}
		}
		else {
			// 已释放：按分销退款口径扣可提现佣金，不足记欠款并冲减累计佣金
			BigDecimal availableDeduct = available.min(rewardAmount);
			distributor.setAvailableCommission(available.subtract(availableDeduct));
			distributor.setCommissionDebt(defaultZero(distributor.getCommissionDebt())
				.add(rewardAmount.subtract(availableDeduct)));
			distributor.setTotalCommission(defaultZero(distributor.getTotalCommission())
				.subtract(rewardAmount).max(BigDecimal.ZERO));
		}
		if (!distributionUserService.updateById(distributor)) {
			throw new ArynBusinessException("转交奖励扣回更新佣金冲突，请重试");
		}
		if (rewardAmount.signum() > 0) {
			DistributionCommissionFlow flow = new DistributionCommissionFlow();
			flow.setUserId(reward.getDistributorUserId());
			flow.setBizOrderId(bizOrderId);
			flow.setFlowType(CommissionFlowTypeEnum.EXPENSE.getCode());
			flow.setAmount(rewardAmount);
			flow.setBalanceAfter(defaultZero(distributor.getAvailableCommission()));
			flow.setRemark("首单全额退款扣回转交奖励");
			if (!distributionCommissionFlowService.save(flow)) {
				throw new ArynBusinessException("转交奖励扣回佣金流水保存失败");
			}
		}
		log.info("转交奖励扣回完成 bizOrderId={}, distributorUserId={}, rewardAmount={}, originStatus={}", bizOrderId,
				reward.getDistributorUserId(), rewardAmount, originStatus);
		return true;
	}

	private IPage<SalesTransferRewardVO> convertWithNickname(IPage<SalesTransferReward> rewardPage) {
		List<String> userIds = rewardPage.getRecords()
			.stream()
			.flatMap(reward -> java.util.stream.Stream
				.of(reward.getCustomerUserId(), reward.getDistributorUserId()))
			.toList();
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(userIds);
		return rewardPage.convert(reward -> {
			SalesTransferRewardVO vo = new SalesTransferRewardVO();
			BeanUtils.copyProperties(reward, vo);
			UserInfoVO customer = userMap.get(reward.getCustomerUserId());
			if (customer != null) {
				vo.setNickname(customer.getNickname());
			}
			UserInfoVO distributor = userMap.get(reward.getDistributorUserId());
			if (distributor != null) {
				vo.setDistributorNickname(distributor.getNickname());
			}
			return vo;
		});
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
