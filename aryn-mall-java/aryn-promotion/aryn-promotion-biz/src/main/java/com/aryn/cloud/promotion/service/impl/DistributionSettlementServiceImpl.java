package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.enums.CommissionFlowTypeEnum;
import com.aryn.cloud.promotion.api.enums.DistributionOrderStatusEnum;
import com.aryn.cloud.promotion.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean settleOrder(DistributionSettleDTO dto) {
		// 1. 幂等检查：已存在则跳过
		DistributionOrder exists = distributionOrderService.getOne(Wrappers.<DistributionOrder>lambdaQuery()
			.eq(DistributionOrder::getBizOrderId, dto.getOrderId())
			.last("limit 1"));
		if (exists != null) {
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

		// 5. 计算一级佣金
		BigDecimal rate = config.getCommissionRate() == null
			? new BigDecimal(MallEventConstants.DEFAULT_COMMISSION_RATE) : config.getCommissionRate();
		BigDecimal commissionAmount = dto.getOrderAmount().multiply(rate).setScale(2, RoundingMode.DOWN);

		if (commissionAmount.compareTo(BigDecimal.ZERO) <= 0) {
			log.info("佣金为0，跳过结算 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 6. 创建分销订单（待结算状态），利用唯一索引保证幂等
		DistributionOrder order = new DistributionOrder();
		order.setBizOrderId(dto.getOrderId());
		order.setBuyerUserId(dto.getBuyerUserId());
		order.setDistributorUserId(distributor.getUserId());
		order.setOrderAmount(dto.getOrderAmount());
		order.setCommissionAmount(commissionAmount);
		order.setCommissionLevel(1);
		order.setStatus(DistributionOrderStatusEnum.STATUS_1.getCode());
		order.setSettleTime(LocalDateTime.now());
		try {
			distributionOrderService.save(order);
		}
		catch (DuplicateKeyException e) {
			log.warn("并发结算，分销订单已存在 bizOrderId={}", dto.getOrderId());
			return Boolean.FALSE;
		}

		// 7. 更新分销员佣金（乐观锁保护）
		BigDecimal newTotal = distributor.getTotalCommission() == null
			? commissionAmount : distributor.getTotalCommission().add(commissionAmount);
		BigDecimal newAvailable = distributor.getAvailableCommission() == null
			? commissionAmount : distributor.getAvailableCommission().add(commissionAmount);
		distributor.setTotalCommission(newTotal);
		distributor.setAvailableCommission(newAvailable);
		boolean updated = distributionUserService.updateById(distributor);
		if (!updated) {
			log.warn("佣金更新失败（乐观锁冲突），将重试 distributorId={}", distributor.getUserId());
			throw new ArynBusinessException("佣金更新冲突，请重试");
		}

		// 8. 记录佣金流水
		DistributionCommissionFlow flow = new DistributionCommissionFlow();
		flow.setUserId(distributor.getUserId());
		flow.setBizOrderId(dto.getOrderId());
		flow.setFlowType(CommissionFlowTypeEnum.INCOME.getCode());
		flow.setAmount(commissionAmount);
		flow.setBalanceAfter(newAvailable);
		flow.setRemark("订单结算佣金");
		distributionCommissionFlowService.save(flow);

		log.info("分销结算成功 bizOrderId={}, distributorId={}, commissionAmount={}",
			dto.getOrderId(), distributor.getUserId(), commissionAmount);

		// 9. 二级佣金：如果分销员自己也有邀请人，则给邀请人的邀请人结算二级佣金
		settleLevel2Commission(dto, distributor, config);

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
		BigDecimal commission2 = dto.getOrderAmount().multiply(rate2).setScale(2, RoundingMode.DOWN);

		if (commission2.compareTo(BigDecimal.ZERO) <= 0) {
			return;
		}

		// 创建二级分销订单
		DistributionOrder order2 = new DistributionOrder();
		order2.setBizOrderId(dto.getOrderId() + "_L2");
		order2.setBuyerUserId(dto.getBuyerUserId());
		order2.setDistributorUserId(level2User.getUserId());
		order2.setOrderAmount(dto.getOrderAmount());
		order2.setCommissionAmount(commission2);
		order2.setCommissionLevel(2);
		order2.setStatus(DistributionOrderStatusEnum.STATUS_1.getCode());
		order2.setSettleTime(LocalDateTime.now());
		try {
			distributionOrderService.save(order2);
		}
		catch (DuplicateKeyException e) {
			log.warn("并发结算，二级分销订单已存在 bizOrderId={}", dto.getOrderId());
			return;
		}

		// 更新二级分销员佣金
		BigDecimal newTotal2 = level2User.getTotalCommission() == null
			? commission2 : level2User.getTotalCommission().add(commission2);
		BigDecimal newAvailable2 = level2User.getAvailableCommission() == null
			? commission2 : level2User.getAvailableCommission().add(commission2);
		level2User.setTotalCommission(newTotal2);
		level2User.setAvailableCommission(newAvailable2);
		boolean updated2 = distributionUserService.updateById(level2User);
		if (!updated2) {
			log.warn("二级佣金更新失败（乐观锁冲突） level2UserId={}", level2User.getUserId());
			throw new ArynBusinessException("二级佣金更新冲突，请重试");
		}

		// 记录二级佣金流水
		DistributionCommissionFlow flow2 = new DistributionCommissionFlow();
		flow2.setUserId(level2User.getUserId());
		flow2.setBizOrderId(dto.getOrderId());
		flow2.setFlowType(CommissionFlowTypeEnum.INCOME.getCode());
		flow2.setAmount(commission2);
		flow2.setBalanceAfter(newAvailable2);
		flow2.setRemark("二级订单结算佣金");
		distributionCommissionFlowService.save(flow2);

		log.info("二级分销结算成功 bizOrderId={}, level2DistributorId={}, commissionAmount={}",
			dto.getOrderId(), level2User.getUserId(), commission2);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void refundCommission(String orderId, BigDecimal refundAmount) {
		// 1. 查询分销订单
		DistributionOrder distOrder = distributionOrderService.getOne(Wrappers.<DistributionOrder>lambdaQuery()
			.eq(DistributionOrder::getBizOrderId, orderId)
			.last("limit 1"));
		if (distOrder == null) {
			log.info("无分销订单，跳过退款回退 orderId={}", orderId);
			return;
		}

		// 2. 按比例计算回退佣金
		BigDecimal refundCommission = distOrder.getCommissionAmount()
			.multiply(refundAmount)
			.divide(distOrder.getOrderAmount(), 2, RoundingMode.DOWN);

		if (refundCommission.compareTo(BigDecimal.ZERO) <= 0) {
			log.info("回退佣金为0，跳过 orderId={}", orderId);
			return;
		}

		// 3. 扣减分销员佣金（乐观锁保护）
		DistributionUser distributor = distributionUserService.getByUserId(distOrder.getDistributorUserId());
		if (distributor == null) {
			log.warn("分销员不存在，无法回退佣金 distributorId={}", distOrder.getDistributorUserId());
			return;
		}

		BigDecimal newTotal = distributor.getTotalCommission().subtract(refundCommission);
		BigDecimal newAvailable = distributor.getAvailableCommission().subtract(refundCommission);
		// 可提现佣金不能为负
		if (newAvailable.compareTo(BigDecimal.ZERO) < 0) {
			newAvailable = BigDecimal.ZERO;
		}
		if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
			newTotal = BigDecimal.ZERO;
		}
		distributor.setTotalCommission(newTotal);
		distributor.setAvailableCommission(newAvailable);
		boolean updated = distributionUserService.updateById(distributor);
		if (!updated) {
			log.warn("退款佣金扣减失败（乐观锁冲突） distributorId={}", distributor.getUserId());
			throw new ArynBusinessException("佣金更新冲突，请重试");
		}

		// 4. 更新分销订单状态为已退款
		distOrder.setStatus(DistributionOrderStatusEnum.STATUS_2.getCode());
		distributionOrderService.updateById(distOrder);

		// 5. 记录佣金流水
		DistributionCommissionFlow flow = new DistributionCommissionFlow();
		flow.setUserId(distributor.getUserId());
		flow.setBizOrderId(orderId);
		flow.setFlowType(CommissionFlowTypeEnum.EXPENSE.getCode());
		flow.setAmount(refundCommission);
		flow.setBalanceAfter(newAvailable);
		flow.setRemark("退款回退佣金");
		distributionCommissionFlowService.save(flow);

		log.info("退款回退佣金成功 orderId={}, distributorId={}, refundCommission={}",
			orderId, distributor.getUserId(), refundCommission);
	}

}
