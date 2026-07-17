package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.api.entity.DistributionRefundRecord;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.enums.CommissionFlowTypeEnum;
import com.aryn.cloud.promotion.api.enums.DistributionOrderStatusEnum;
import com.aryn.cloud.promotion.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分销结算服务实现
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionSettlementServiceImpl implements IDistributionSettlementService {

	private final IDistributionConfigService distributionConfigService;

	private final IDistributionOrderService distributionOrderService;

	private final IDistributionUserService distributionUserService;

	private final IDistributionCommissionFlowService distributionCommissionFlowService;

	private final IDistributionRefundRecordService distributionRefundRecordService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean settleOrder(DistributionSettleDTO dto) {
		// 一级佣金订单承担支付事件幂等键。
		DistributionOrder exists = distributionOrderService.getOne(Wrappers.<DistributionOrder>lambdaQuery()
			.eq(DistributionOrder::getBizOrderId, dto.getOrderId())
			.eq(DistributionOrder::getCommissionLevel, 1)
			.last("limit 1"));
		if (exists != null) {
			applyRecordedRefunds(dto.getOrderId());
			log.info("分销订单已存在，跳过结算 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 2. 查询分销配置，未启用则不结算
		DistributionConfig config = distributionConfigService.getActiveConfig();
		if (config == null) {
			log.info("无启用的分销配置，跳过结算 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 3. 查询买家的分销关系
		DistributionUser buyer = distributionUserService.getByUserId(dto.getBuyerUserId());
		if (buyer == null || buyer.getInviterUserId() == null || buyer.getInviterUserId().isBlank()) {
			log.info("买家无分销关系，跳过结算 buyerUserId={}", dto.getBuyerUserId());
			return Boolean.FALSE;
		}

		// 4. 查询分销员（邀请人），需为启用状态
		DistributionUser distributor = distributionUserService.getByUserId(buyer.getInviterUserId());
		if (distributor == null || !MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(distributor.getStatus())) {
			log.info("分销员不存在或已禁用，跳过结算 inviterUserId={}", buyer.getInviterUserId());
			return Boolean.FALSE;
		}

		BigDecimal commissionBaseAmount = dto.getOrderAmount() == null
			? BigDecimal.ZERO : dto.getOrderAmount().max(BigDecimal.ZERO);
		if (commissionBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
			log.info("佣金基数为0，跳过结算 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 5. 计算一级待结算佣金
		BigDecimal rate = config.getCommissionRate() == null
			? new BigDecimal(MallEventConstants.DEFAULT_COMMISSION_RATE) : config.getCommissionRate();
		BigDecimal commissionAmount = commissionBaseAmount.multiply(rate).setScale(2, RoundingMode.DOWN);

		if (commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
			log.info("佣金为0，跳过结算 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 6. 创建待结算分销订单，利用唯一索引保证幂等。
		DistributionOrder order = createPendingOrder(dto, config, distributor.getUserId(), commissionBaseAmount,
			commissionAmount, 1);
		try {
			distributionOrderService.save(order);
		}
		catch (DuplicateKeyException e) {
			log.warn("并发结算，分销订单已存在 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		addPendingCommission(distributor, commissionAmount);

		log.info("分销佣金进入待结算 bizOrderId={}, distributorId={}, commissionAmount={}",
			dto.getOrderId(), distributor.getUserId(), commissionAmount);

		// 9. 二级佣金：如果分销员自己也有邀请人，则给邀请人的邀请人结算二级佣金
		settleLevel2Commission(dto, distributor, config);
		applyRecordedRefunds(dto.getOrderId());

		return Boolean.TRUE;
	}

	/**
	 * 二级佣金结算：给分销员的邀请人结算
	 */
	private void settleLevel2Commission(DistributionSettleDTO dto, DistributionUser distributor, DistributionConfig config) {
		if (distributor.getInviterUserId() == null || distributor.getInviterUserId().isBlank()) {
			return;
		}

		DistributionUser level2User = distributionUserService.getByUserId(distributor.getInviterUserId());
		if (level2User == null || !MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(level2User.getStatus())) {
			log.info("二级分销员不存在或已禁用，跳过 level2UserId={}", distributor.getInviterUserId());
			return;
		}

		BigDecimal rate2 = config.getCommissionRateLevel2() == null
			? new BigDecimal(MallEventConstants.DEFAULT_COMMISSION_RATE_LEVEL2) : config.getCommissionRateLevel2();
		BigDecimal commissionBaseAmount = dto.getOrderAmount().max(BigDecimal.ZERO);
		BigDecimal commission2 = commissionBaseAmount.multiply(rate2).setScale(2, RoundingMode.DOWN);

		if (commission2.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}

		// 创建二级分销订单
		DistributionOrder order2 = createPendingOrder(dto, config, level2User.getUserId(), commissionBaseAmount,
			commission2, 2);
		try {
			distributionOrderService.save(order2);
		}
		catch (DuplicateKeyException e) {
			log.warn("并发结算，二级分销订单已存在 bizOrderId={}", dto.getOrderId());
			return;
		}

		addPendingCommission(level2User, commission2);

		log.info("二级分销佣金进入待结算 bizOrderId={}, level2DistributorId={}, commissionAmount={}",
			dto.getOrderId(), level2User.getUserId(), commission2);
	}

	private DistributionOrder createPendingOrder(DistributionSettleDTO dto, DistributionConfig config,
			String distributorUserId, BigDecimal commissionBaseAmount, BigDecimal commissionAmount, int level) {
		DistributionOrder order = new DistributionOrder();
		order.setBizOrderId(dto.getOrderId());
		order.setBuyerUserId(dto.getBuyerUserId());
		order.setDistributorUserId(distributorUserId);
		order.setOrderAmount(dto.getPaymentAmount() == null ? commissionBaseAmount : dto.getPaymentAmount());
		order.setFreightAmount(dto.getFreightAmount() == null ? BigDecimal.ZERO : dto.getFreightAmount());
		order.setCommissionBaseAmount(commissionBaseAmount);
		order.setCommissionAmount(commissionAmount);
		order.setRefundedBaseAmount(BigDecimal.ZERO);
		order.setRefundedCommissionAmount(BigDecimal.ZERO);
		order.setCommissionLevel(level);
		order.setStatus(DistributionOrderStatusEnum.STATUS_0.getCode());
		int cycleDays = config.getSettleCycleDays() == null ? 7 : Math.max(config.getSettleCycleDays(), 0);
		order.setSettleAt(LocalDateTime.now().plusDays(cycleDays));
		return order;
	}

	private void addPendingCommission(DistributionUser user, BigDecimal amount) {
		BigDecimal pending = user.getPendingCommission() == null ? BigDecimal.ZERO : user.getPendingCommission();
		user.setPendingCommission(pending.add(amount));
		if (!distributionUserService.updateById(user)) {
			throw new ArynBusinessException("待结算佣金更新冲突，请重试");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean settlePendingOrder(String distributionOrderId) {
		DistributionOrder order = distributionOrderService.getById(distributionOrderId);
		if (order == null || !DistributionOrderStatusEnum.STATUS_0.getCode().equals(order.getStatus())) {
			return Boolean.FALSE;
		}

		DistributionUser distributor = distributionUserService.getByUserId(order.getDistributorUserId());
		if (distributor == null) {
			throw new ArynBusinessException("分销员不存在，无法释放待结算佣金");
		}

		BigDecimal commission = defaultZero(order.getCommissionAmount())
			.subtract(defaultZero(order.getRefundedCommissionAmount()))
			.max(BigDecimal.ZERO);
		BigDecimal pending = defaultZero(distributor.getPendingCommission());
		if (pending.compareTo(commission) < 0) {
			throw new ArynBusinessException("待结算佣金余额不足，无法完成结算");
		}

		BigDecimal debt = defaultZero(distributor.getCommissionDebt());
		BigDecimal debtOffset = commission.min(debt);
		BigDecimal released = commission.subtract(debtOffset);
		distributor.setPendingCommission(pending.subtract(commission));
		distributor.setCommissionDebt(debt.subtract(debtOffset));
		distributor.setAvailableCommission(defaultZero(distributor.getAvailableCommission()).add(released));
		distributor.setTotalCommission(defaultZero(distributor.getTotalCommission()).add(commission));
		if (!distributionUserService.updateById(distributor)) {
			throw new ArynBusinessException("佣金余额更新冲突，请重试");
		}

		order.setStatus(DistributionOrderStatusEnum.STATUS_1.getCode());
		order.setSettleTime(LocalDateTime.now());
		if (!distributionOrderService.updateById(order)) {
			throw new ArynBusinessException("分销订单更新冲突，请重试");
		}

		DistributionCommissionFlow flow = new DistributionCommissionFlow();
		flow.setUserId(distributor.getUserId());
		flow.setBizOrderId(order.getBizOrderId());
		flow.setFlowType(CommissionFlowTypeEnum.INCOME.getCode());
		flow.setAmount(commission);
		flow.setBalanceAfter(distributor.getAvailableCommission());
		flow.setRemark(debtOffset.signum() > 0 ? "佣金结算（已抵扣欠款）" : "佣金结算");
		if (!distributionCommissionFlowService.save(flow)) {
			throw new ArynBusinessException("佣金流水保存失败");
		}

		return Boolean.TRUE;
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean refundCommission(String orderId, String refundNo, BigDecimal refundAmount,
			BigDecimal refundBaseAmount) {
		if (orderId == null || orderId.isBlank() || refundNo == null || refundNo.isBlank()
				|| refundBaseAmount == null || refundBaseAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return Boolean.FALSE;
		}

		DistributionRefundRecord exists = distributionRefundRecordService.getOne(
			Wrappers.<DistributionRefundRecord>lambdaQuery()
				.eq(DistributionRefundRecord::getRefundNo, refundNo)
				.last("limit 1"));
		if (exists != null) {
			if (!CommonConstants.NO.equals(exists.getApplied())) {
				log.info("分销退款已处理，跳过 refundNo={}", refundNo);
				return Boolean.FALSE;
			}
			return applyRefundRecord(exists);
		}

		DistributionRefundRecord record = new DistributionRefundRecord();
		record.setRefundNo(refundNo);
		record.setBizOrderId(orderId);
		record.setRefundAmount(refundAmount == null ? refundBaseAmount : refundAmount);
		record.setRefundBaseAmount(refundBaseAmount);
		record.setApplied(CommonConstants.NO);
		try {
			if (!distributionRefundRecordService.save(record)) {
				throw new ArynBusinessException("分销退款记录保存失败");
			}
		}
		catch (DuplicateKeyException e) {
			log.info("分销退款并发重复，跳过 refundNo={}", refundNo);
			return Boolean.FALSE;
		}

		return applyRefundRecord(record);
	}

	@Override
	public List<String> listPendingRefundIds() {
		return distributionRefundRecordService.list(
			Wrappers.<DistributionRefundRecord>lambdaQuery()
				.eq(DistributionRefundRecord::getApplied, CommonConstants.NO)
				.orderByAsc(DistributionRefundRecord::getCreateTime))
			.stream()
			.map(DistributionRefundRecord::getId)
			.toList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public Boolean replayPendingRefund(String refundRecordId) {
		DistributionRefundRecord record = distributionRefundRecordService.getById(refundRecordId);
		if (record == null || !CommonConstants.NO.equals(record.getApplied())) {
			return Boolean.FALSE;
		}
		return applyRefundRecord(record);
	}

	private void applyRecordedRefunds(String orderId) {
		List<DistributionRefundRecord> records = distributionRefundRecordService.list(
			Wrappers.<DistributionRefundRecord>lambdaQuery()
				.eq(DistributionRefundRecord::getBizOrderId, orderId)
				.eq(DistributionRefundRecord::getApplied, CommonConstants.NO)
				.orderByAsc(DistributionRefundRecord::getCreateTime));
		for (DistributionRefundRecord record : records) {
			applyRefundRecord(record);
		}
	}

	private boolean applyRefundRecord(DistributionRefundRecord record) {
		List<DistributionOrder> orders = distributionOrderService.list(
			Wrappers.<DistributionOrder>lambdaQuery()
				.eq(DistributionOrder::getBizOrderId, record.getBizOrderId()));
		if (orders.isEmpty()) {
			log.info("分销退款已记录，等待支付事件创建佣金订单 orderId={}", record.getBizOrderId());
			return false;
		}
		for (DistributionOrder order : orders) {
			refundDistributionOrder(order, record);
		}
		record.setApplied(CommonConstants.YES);
		record.setAppliedTime(LocalDateTime.now());
		if (!distributionRefundRecordService.updateById(record)) {
			throw new ArynBusinessException("分销退款应用状态更新失败");
		}
		return true;
	}

	private boolean refundDistributionOrder(DistributionOrder order, DistributionRefundRecord record) {
		boolean legacyOrder = order.getCommissionBaseAmount() == null;
		BigDecimal commissionBase = legacyOrder
			? defaultZero(order.getOrderAmount()) : defaultZero(order.getCommissionBaseAmount());
		if (commissionBase.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		BigDecimal refundBaseAmount = legacyOrder
			? defaultZero(record.getRefundAmount()) : defaultZero(record.getRefundBaseAmount());
		BigDecimal refundedBase = defaultZero(order.getRefundedBaseAmount()).min(commissionBase);
		BigDecimal newRefundedBase = refundedBase.add(refundBaseAmount).min(commissionBase);
		BigDecimal commissionAmount = defaultZero(order.getCommissionAmount());
		BigDecimal previousRefundedCommission = defaultZero(order.getRefundedCommissionAmount());
		BigDecimal targetRefundedCommission = commissionAmount.multiply(newRefundedBase)
			.divide(commissionBase, 2, RoundingMode.DOWN)
			.min(commissionAmount);
		BigDecimal refundCommission = targetRefundedCommission.subtract(previousRefundedCommission)
			.max(BigDecimal.ZERO);

		DistributionUser distributor = null;
		if (refundCommission.compareTo(BigDecimal.ZERO) > 0) {
			distributor = distributionUserService.getByUserId(order.getDistributorUserId());
			if (distributor == null) {
				throw new ArynBusinessException("分销员不存在，无法回退佣金");
			}
			if (DistributionOrderStatusEnum.STATUS_0.getCode().equals(order.getStatus())) {
				BigDecimal pending = defaultZero(distributor.getPendingCommission());
				if (pending.compareTo(refundCommission) < 0) {
					throw new ArynBusinessException("待结算佣金不足，无法回退退款佣金");
				}
				distributor.setPendingCommission(pending.subtract(refundCommission));
			}
			else {
				BigDecimal available = defaultZero(distributor.getAvailableCommission());
				BigDecimal availableDeduction = available.min(refundCommission);
				distributor.setAvailableCommission(available.subtract(availableDeduction));
				distributor.setCommissionDebt(defaultZero(distributor.getCommissionDebt())
					.add(refundCommission.subtract(availableDeduction)));
				distributor.setTotalCommission(defaultZero(distributor.getTotalCommission())
					.subtract(refundCommission).max(BigDecimal.ZERO));
			}
			if (!distributionUserService.updateById(distributor)) {
				throw new ArynBusinessException("退款佣金更新冲突，请重试");
			}
		}

		order.setRefundedBaseAmount(newRefundedBase);
		order.setRefundedCommissionAmount(targetRefundedCommission);
		if (newRefundedBase.compareTo(commissionBase) >= 0) {
			order.setStatus(DistributionOrderStatusEnum.STATUS_2.getCode());
		}
		if (!distributionOrderService.updateById(order)) {
			throw new ArynBusinessException("分销退款订单更新冲突，请重试");
		}

		if (refundCommission.compareTo(BigDecimal.ZERO) > 0) {
			DistributionCommissionFlow flow = new DistributionCommissionFlow();
			flow.setUserId(distributor.getUserId());
			flow.setBizOrderId(record.getRefundNo());
			flow.setFlowType(CommissionFlowTypeEnum.EXPENSE.getCode());
			flow.setAmount(refundCommission);
			flow.setBalanceAfter(defaultZero(distributor.getAvailableCommission()));
			flow.setRemark("退款回退佣金");
			if (!distributionCommissionFlowService.save(flow)) {
				throw new ArynBusinessException("退款佣金流水保存失败");
			}
		}

		log.info("退款回退佣金成功 orderId={}, refundNo={}, level={}, refundCommission={}",
			order.getBizOrderId(), record.getRefundNo(), order.getCommissionLevel(), refundCommission);
		return true;
	}

}
