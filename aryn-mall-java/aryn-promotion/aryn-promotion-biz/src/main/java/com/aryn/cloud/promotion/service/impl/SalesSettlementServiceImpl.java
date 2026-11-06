package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesRefundRecord;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerBindSourceEnum;
import com.aryn.cloud.promotion.api.enums.SalesOrderStatusEnum;
import com.aryn.cloud.promotion.api.enums.SalesStaffStatusEnum;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutAdjustmentService;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.service.ISalesRefundRecordService;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.promotion.service.ISalesTransferRewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售业绩结算服务实现
 *
 * <p><b>支付口径（2026-11-05 与 Lead 对齐）</b>：本仓库 {@code order_info.payment_price} 与
 * {@code actual_pay_price} 均为<b>含运费</b>口径，因此提成基数 = 实付 - 运费：
 * <ul>
 *   <li>在线支付：{@code max(0, paymentPrice - freightPrice)}；</li>
 *   <li>货到付款（paymentType=3）：{@code max(0, actualPayPrice - freightPrice)}，
 *       {@code actualPayPrice} 为空时回退 {@code paymentPrice - freightPrice}。</li>
 * </ul>
 * 提成 = 基数 × 比例（销售个人比例为空取全局默认），2 位小数向下取整。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesSettlementServiceImpl implements ISalesSettlementService {

	/** 退款记录等待业绩单的最长重放天数：超过仍未生成业绩单说明该订单不计销售业绩 */
	private static final long REFUND_REPLAY_MAX_WAIT_DAYS = 7L;

	private final ISalesStaffService salesStaffService;

	private final ISalesCustomerClaimService salesCustomerClaimService;

	private final ISalesCustomerBindService salesCustomerBindService;

	private final SalesBindWriter salesBindWriter;

	private final ISalesOrderService salesOrderService;

	private final ISalesConfigService salesConfigService;

	private final ISalesRefundRecordService salesRefundRecordService;

	private final ISalesTransferRewardService salesTransferRewardService;

	private final ISalesPayoutAdjustmentService salesPayoutAdjustmentService;

	private final ISalesPayoutService salesPayoutService;

	private final IDistributionUserService distributionUserService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean handlePaySuccess(OrderPaySuccessEvent event) {
		if (event == null) {
			return Boolean.FALSE;
		}
		String bizOrderId = event.getOrderId();
		String salesStaffId = event.getSalesStaffId();
		if (bizOrderId == null || bizOrderId.isBlank()) {
			return Boolean.FALSE;
		}
		if (salesStaffId == null || salesStaffId.isBlank()) {
			log.debug("订单无归属销售快照，跳过销售业绩 bizOrderId={}", bizOrderId);
			return Boolean.FALSE;
		}
		// 幂等：业绩单唯一键 (tenant, biz_order_id)
		SalesOrder exists = salesOrderService.getByBizOrderId(bizOrderId);
		if (exists != null) {
			applyRecordedRefunds(bizOrderId);
			log.info("销售业绩单已存在，跳过 bizOrderId={}", bizOrderId);
			return Boolean.FALSE;
		}
		SalesStaff staff = salesStaffService.getById(salesStaffId);
		if (staff == null) {
			log.warn("归属销售不存在，跳过销售业绩 bizOrderId={}, salesStaffId={}", bizOrderId, salesStaffId);
			return Boolean.FALSE;
		}
		if (!SalesStaffStatusEnum.STATUS_0.getCode().equals(staff.getStatus())) {
			log.info("归属销售已离职，不再产生新业绩 bizOrderId={}, salesStaffId={}", bizOrderId, salesStaffId);
			return Boolean.FALSE;
		}
		String buyerUserId = event.getUserId();
		if (buyerUserId != null && buyerUserId.equals(staff.getMemberUserId())) {
			log.info("销售本人自购不计业绩 bizOrderId={}, salesStaffId={}", bizOrderId, salesStaffId);
			return Boolean.FALSE;
		}

		SalesConfig config = salesConfigService.getActiveConfigOrDefaults();
		BigDecimal commissionBase = resolveCommissionBase(event);
		if (commissionBase.signum() <= 0) {
			log.info("提成基数为0，跳过销售业绩 bizOrderId={}", bizOrderId);
			return Boolean.FALSE;
		}

		// 1. 首单转交：认领 → 正式归属 + 换绑日志 + 原分销员一次性奖励
		completeTransferIfNeeded(event, staff, config, commissionBase);

		// 2. 生成业绩单（比例快照，之后改比例不影响已生成业绩）
		BigDecimal rate = staff.getCommissionRate() != null ? staff.getCommissionRate()
				: config.getDefaultCommissionRate();
		BigDecimal commissionAmount = commissionBase.multiply(rate).setScale(2, RoundingMode.DOWN);
		LocalDateTime payTime = event.getPaymentTime() == null ? LocalDateTime.now() : event.getPaymentTime();
		int riskDays = config.getRefundRiskDays() == null
				? SalesConfigServiceImpl.DEFAULT_REFUND_RISK_DAYS : config.getRefundRiskDays();
		BigDecimal paymentAmount = resolvePaymentAmount(event, commissionBase);
		BigDecimal freightAmount = event.getFreightPrice() == null ? BigDecimal.ZERO : event.getFreightPrice();

		SalesOrder salesOrder = new SalesOrder();
		salesOrder.setBizOrderId(bizOrderId);
		salesOrder.setOrderNo(event.getOrderNo());
		salesOrder.setBuyerUserId(buyerUserId);
		salesOrder.setSalesStaffId(staff.getId());
		salesOrder.setStaffName(staff.getStaffName());
		salesOrder.setDeptId(staff.getDeptId());
		salesOrder.setPaymentAmount(paymentAmount);
		salesOrder.setFreightAmount(freightAmount);
		salesOrder.setCommissionBase(commissionBase);
		salesOrder.setCommissionRate(rate);
		salesOrder.setCommissionAmount(commissionAmount);
		salesOrder.setRefundedBase(BigDecimal.ZERO);
		salesOrder.setRefundedCommission(BigDecimal.ZERO);
		// 支付即「可发放」：settle_at 仅作退款风险观察期标记，不再作为 N 天放行门槛
		salesOrder.setStatus(SalesOrderStatusEnum.STATUS_1.getCode());
		salesOrder.setPayTime(payTime);
		salesOrder.setSettleAt(payTime.plusDays(riskDays));
		if (!salesOrderService.saveIdempotent(salesOrder)) {
			log.info("并发支付事件，业绩单已存在 bizOrderId={}", bizOrderId);
			return Boolean.FALSE;
		}
		log.info("销售业绩单生成 bizOrderId={}, salesStaffId={}, base={}, rate={}, commission={}", bizOrderId,
				staff.getId(), commissionBase, rate, commissionAmount);

		// 业绩单延迟生成时重开此前被「超期放弃」的退款记录，避免漏扣提成（此时该订单必然还没有业绩单）
		salesRefundRecordService.reopenAbandonedByBizOrderId(bizOrderId);

		// 3. 退款早于支付时补应用
		applyRecordedRefunds(bizOrderId);
		return Boolean.TRUE;
	}

	/**
	 * 业绩单实付金额口径：货到付款取 actualPayPrice（实收含运费，>0 时），为空回退 paymentPrice；
	 * 在线支付直接取 paymentPrice（含运费）。
	 */
	BigDecimal resolvePaymentAmount(OrderPaySuccessEvent event, BigDecimal commissionBase) {
		boolean cashOnDelivery = MallOrderConstants.PAYMENT_TYPE_3.equals(event.getPaymentType());
		BigDecimal actualPayPrice = event.getActualPayPrice();
		if (cashOnDelivery && actualPayPrice != null && actualPayPrice.signum() > 0) {
			return actualPayPrice;
		}
		return event.getPaymentPrice() == null ? commissionBase : event.getPaymentPrice();
	}

	/**
	 * 首单转交：以订单上的销售快照为准完成客户归属与分销员一次性奖励。
	 * <p>客户已有正式归属：归快照销售则幂等返回；归其他销售则保留后台归属、不覆盖。
	 * 客户无正式归属：快照销售只可能来自分享直绑或待转交认领，支付即完成转交；
	 * 被替换/已过期的其他未完成认领统一作废，避免后续重复转交。
	 */
	private void completeTransferIfNeeded(OrderPaySuccessEvent event, SalesStaff staff, SalesConfig config,
			BigDecimal commissionBase) {
		String buyerUserId = event.getUserId();
		if (buyerUserId == null || buyerUserId.isBlank()) {
			return;
		}
		SalesCustomerBind bind = salesCustomerBindService.getByCustomerUserId(buyerUserId);
		if (bind != null) {
			if (staff.getId().equals(bind.getSalesStaffId())) {
				log.info("客户已归属该销售，无需转交 customerUserId={}, salesStaffId={}, bizOrderId={}", buyerUserId,
						staff.getId(), event.getOrderId());
			}
			else {
				log.warn("客户已归属其他销售，保留后台归属不覆盖 customerUserId={}, snapshotStaffId={}, boundStaffId={}, "
						+ "bizOrderId={}", buyerUserId, staff.getId(), bind.getSalesStaffId(), event.getOrderId());
			}
			return;
		}
		// 当前分销上级：原关系展示名 + 一次性奖励对象
		DistributionUser buyerDistribution = distributionUserService.getByUserId(buyerUserId);
		String distributorUserId = buyerDistribution == null ? null : buyerDistribution.getInviterUserId();
		DistributionUser distributor = null;
		if (distributorUserId != null && !distributorUserId.isBlank()) {
			distributor = distributionUserService.getByUserId(distributorUserId);
		}
		String distributorDisplayName = distributor == null || distributor.getNickname() == null
			? null : "分销员：" + distributor.getNickname();
		// 快照销售仍有未过期待认领时置为已转交；被替换/过期认领由下面的 invalidate 统一作废
		SalesCustomerClaim claim = salesCustomerClaimService.getPendingClaim(buyerUserId, staff.getId());
		if (claim != null) {
			salesCustomerClaimService.markTransferred(claim.getId());
		}
		else {
			log.info("首单转交无未过期待认领，按订单快照直接转交 customerUserId={}, salesStaffId={}, bizOrderId={}",
					buyerUserId, staff.getId(), event.getOrderId());
		}
		salesBindWriter.upsertBind(buyerUserId, staff, SalesCustomerBindSourceEnum.CLAIM.getCode(), null,
				distributorDisplayName, "首单转交");
		salesCustomerClaimService.invalidatePendingClaims(buyerUserId);

		BigDecimal rewardRate = config.getReferralRewardRate();
		if (rewardRate != null && rewardRate.signum() > 0 && distributorUserId != null
				&& !distributorUserId.isBlank()) {
			salesTransferRewardService.grantReward(buyerUserId, distributorUserId, staff.getId(), event.getOrderId(),
					commissionBase, rewardRate);
		}
		else if (distributorUserId == null || distributorUserId.isBlank()) {
			log.info("客户无分销上级，完成归属但不发放转交奖励 customerUserId={}, salesStaffId={}, bizOrderId={}",
					buyerUserId, staff.getId(), event.getOrderId());
		}
		log.info("客户首单转交完成 customerUserId={}, salesStaffId={}, distributorUserId={}, claimId={}, bizOrderId={}",
				buyerUserId, staff.getId(), distributorUserId, claim == null ? null : claim.getId(),
				event.getOrderId());
	}

	/**
	 * 提成基数：实付不含运费（货到付款取 actualPayPrice，为空回退 paymentPrice），2 位向下取整。
	 */
	BigDecimal resolveCommissionBase(OrderPaySuccessEvent event) {
		BigDecimal paymentPrice = defaultZero(event.getPaymentPrice());
		BigDecimal freightPrice = defaultZero(event.getFreightPrice());
		boolean cashOnDelivery = MallOrderConstants.PAYMENT_TYPE_3.equals(event.getPaymentType());
		BigDecimal actualPayPrice = event.getActualPayPrice();
		BigDecimal payable;
		if (cashOnDelivery && actualPayPrice != null && actualPayPrice.signum() > 0) {
			payable = actualPayPrice;
		}
		else {
			payable = paymentPrice;
		}
		return payable.subtract(freightPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean handleRefund(OrderRefundSuccessEvent event) {
		if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()
				|| event.getRefundNo() == null || event.getRefundNo().isBlank()) {
			return Boolean.FALSE;
		}
		BigDecimal refundBaseAmount = event.getRefundBaseAmount();
		if (refundBaseAmount == null || refundBaseAmount.signum() <= 0) {
			log.info("退款提成基数为0，跳过销售提成扣回 refundNo={}", event.getRefundNo());
			return Boolean.FALSE;
		}
		SalesRefundRecord exists = salesRefundRecordService.getByRefundNo(event.getRefundNo());
		if (exists != null) {
			if (!CommonConstants.NO.equals(exists.getApplied())) {
				log.info("销售退款已应用，跳过 refundNo={}", event.getRefundNo());
				return Boolean.FALSE;
			}
			return applyRefundRecord(exists);
		}
		SalesRefundRecord record = new SalesRefundRecord();
		record.setRefundNo(event.getRefundNo());
		record.setBizOrderId(event.getOrderId());
		record.setRefundAmount(event.getRefundAmount() == null ? refundBaseAmount : event.getRefundAmount());
		record.setRefundBaseAmount(refundBaseAmount);
		record.setApplied(CommonConstants.NO);
		try {
			if (!salesRefundRecordService.save(record)) {
				throw new ArynBusinessException("销售退款记录保存失败");
			}
		}
		catch (DuplicateKeyException e) {
			log.info("销售退款并发重复，跳过 refundNo={}", event.getRefundNo());
			return Boolean.FALSE;
		}
		return applyRefundRecord(record);
	}

	@Override
	public List<String> listPendingRefundIds() {
		return salesRefundRecordService.list(com.baomidou.mybatisplus.core.toolkit.Wrappers
			.<SalesRefundRecord>lambdaQuery()
			.eq(SalesRefundRecord::getApplied, CommonConstants.NO)
			.orderByAsc(SalesRefundRecord::getCreateTime))
			.stream()
			.map(SalesRefundRecord::getId)
			.toList();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public Boolean replayPendingRefund(String refundRecordId) {
		SalesRefundRecord record = salesRefundRecordService.getById(refundRecordId);
		if (record == null || !CommonConstants.NO.equals(record.getApplied())) {
			return Boolean.FALSE;
		}
		return applyRefundRecord(record);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void applyRecordedRefunds(String bizOrderId) {
		if (bizOrderId == null || bizOrderId.isBlank()) {
			return;
		}
		List<SalesRefundRecord> records = salesRefundRecordService.list(
			com.baomidou.mybatisplus.core.toolkit.Wrappers.<SalesRefundRecord>lambdaQuery()
				.eq(SalesRefundRecord::getBizOrderId, bizOrderId)
				.eq(SalesRefundRecord::getApplied, CommonConstants.NO)
				.orderByAsc(SalesRefundRecord::getCreateTime));
		for (SalesRefundRecord record : records) {
			applyRefundRecord(record);
		}
	}

	private boolean applyRefundRecord(SalesRefundRecord record) {
		SalesOrder order = salesOrderService.getByBizOrderId(record.getBizOrderId());
		if (order == null) {
			// 退款早于支付事件：留待支付处理时补应用或定时任务重放
			if (record.getCreateTime() != null
					&& record.getCreateTime().isBefore(LocalDateTime.now().minusDays(REFUND_REPLAY_MAX_WAIT_DAYS))) {
				log.warn("退款记录超过{}天仍无销售业绩单，放弃应用 refundNo={}, bizOrderId={}",
						REFUND_REPLAY_MAX_WAIT_DAYS, record.getRefundNo(), record.getBizOrderId());
				markApplied(record);
				return true;
			}
			log.info("退款已记录，等待支付事件生成销售业绩单 refundNo={}, bizOrderId={}", record.getRefundNo(),
					record.getBizOrderId());
			return false;
		}
		BigDecimal commissionBase = defaultZero(order.getCommissionBase()).max(BigDecimal.ZERO);
		if (commissionBase.signum() <= 0) {
			markApplied(record);
			return true;
		}
		BigDecimal refundBase = defaultZero(record.getRefundBaseAmount()).max(BigDecimal.ZERO);
		BigDecimal refundedBase = defaultZero(order.getRefundedBase()).min(commissionBase);
		BigDecimal newRefundedBase = refundedBase.add(refundBase).min(commissionBase);
		BigDecimal commissionAmount = defaultZero(order.getCommissionAmount());
		BigDecimal previousRefundedCommission = defaultZero(order.getRefundedCommission());
		BigDecimal targetRefundedCommission = commissionAmount.multiply(newRefundedBase)
			.divide(commissionBase, 2, RoundingMode.DOWN)
			.min(commissionAmount);
		BigDecimal deltaCommission = targetRefundedCommission.subtract(previousRefundedCommission)
			.max(BigDecimal.ZERO);
		boolean fullyRefunded = newRefundedBase.compareTo(commissionBase) >= 0;
		String status = order.getStatus();

		if (SalesOrderStatusEnum.STATUS_3.getCode().equals(status)) {
			log.info("销售业绩单已全额退款，跳过 refundNo={}", record.getRefundNo());
			markApplied(record);
			return true;
		}
		if (SalesOrderStatusEnum.STATUS_2.getCode().equals(status)) {
			// 已发放：不改已发放台账，生成负数调整结转到下月
			if (deltaCommission.signum() > 0) {
				salesPayoutAdjustmentService.recordAdjustment(order.getSalesStaffId(), order.getStaffName(),
						order.getBizOrderId(), record.getRefundNo(), deltaCommission.negate());
			}
		}
		order.setRefundedBase(newRefundedBase);
		order.setRefundedCommission(targetRefundedCommission);
		if (fullyRefunded) {
			order.setStatus(SalesOrderStatusEnum.STATUS_3.getCode());
		}
		if (!salesOrderService.updateById(order)) {
			throw new ArynBusinessException("销售业绩单退款更新失败，请重试");
		}
		// 业绩单已进入待确认台账：重算台账金额，避免财务按旧金额发放（其他状态批次由发放台账域 no-op）
		if (order.getPayoutId() != null && !order.getPayoutId().isBlank()) {
			salesPayoutService.recalcPendingPayout(order.getPayoutId());
		}
		if (fullyRefunded) {
			// 首单全额退款：扣回原分销员一次性转交奖励（按 bizOrderId 幂等，无奖励直接跳过）
			salesTransferRewardService.clawbackReward(order.getBizOrderId());
		}
		markApplied(record);
		log.info("销售提成扣回完成 bizOrderId={}, refundNo={}, refundedBase={}, refundedCommission={}, status={}",
				order.getBizOrderId(), record.getRefundNo(), newRefundedBase, targetRefundedCommission,
				order.getStatus());
		return true;
	}

	private void markApplied(SalesRefundRecord record) {
		record.setApplied(CommonConstants.YES);
		record.setAppliedTime(LocalDateTime.now());
		if (!salesRefundRecordService.updateById(record)) {
			throw new ArynBusinessException("销售退款应用状态更新失败");
		}
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
