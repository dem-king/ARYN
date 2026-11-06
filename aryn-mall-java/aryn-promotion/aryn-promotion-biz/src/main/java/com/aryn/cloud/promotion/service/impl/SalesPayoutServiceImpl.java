package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.dto.SalesPayoutConfirmDTO;
import com.aryn.cloud.promotion.api.entity.SalesPayout;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import com.aryn.cloud.promotion.api.enums.SalesPayoutStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;
import com.aryn.cloud.promotion.mapper.SalesPayoutMapper;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutAdjustmentService;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.vo.SalesPayoutAggregateVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售提成月度发放台账服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesPayoutServiceImpl extends ServiceImpl<SalesPayoutMapper, SalesPayout> implements ISalesPayoutService {

	private final ISalesOrderService salesOrderService;

	private final ISalesPayoutAdjustmentService salesPayoutAdjustmentService;

	@Override
	public IPage<SalesPayout> selectAdminPage(Page<SalesPayout> page, SalesPayout query) {
		SalesPayout condition = query == null ? new SalesPayout() : query;
		return this.page(page, Wrappers.<SalesPayout>lambdaQuery()
			.eq(notBlank(condition.getPayoutMonth()), SalesPayout::getPayoutMonth, condition.getPayoutMonth())
			.eq(notBlank(condition.getSalesStaffId()), SalesPayout::getSalesStaffId,
					condition.getSalesStaffId())
			.eq(notBlank(condition.getStatus()), SalesPayout::getStatus, condition.getStatus())
			.orderByDesc(SalesPayout::getPayoutMonth)
			.orderByDesc(SalesPayout::getCreateTime));
	}

	@Override
	public SalesPayout getDetail(String id) {
		SalesPayout payout = this.getById(id);
		if (payout == null) {
			throw new ArynBusinessException("发放台账不存在");
		}
		return payout;
	}

	@Override
	public IPage<SalesOrderVO> pageOrders(Page<SalesPayout> page, String payoutId) {
		Page<com.aryn.cloud.promotion.api.entity.SalesOrder> orderPage = new Page<>(page.getCurrent(),
				page.getSize());
		return salesOrderService.selectPayoutOrderPage(orderPage, payoutId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int generate(String payoutMonth) {
		String month = resolvePayoutMonth(payoutMonth);
		YearMonth payoutYearMonth = YearMonth.parse(month);
		// 不允许提前生成未来月份台账，否则会把当前所有未入批订单提前装进未来账期
		if (payoutYearMonth.isAfter(YearMonth.now().minusMonths(1))) {
			throw new ArynBusinessException("只能生成上月及更早月份的台账");
		}
		// 台账月份次月 1 日 0 点：只纳入该时点之前支付的业绩单，绝不把台账月之后的订单装进本期
		LocalDateTime payTimeBefore = payoutYearMonth.plusMonths(1).atDay(1).atStartOfDay();
		List<SalesPayoutAggregateVO> aggregates = new ArrayList<>(
				salesOrderService.aggregatePayoutOrders(payTimeBefore));
		// 只有退款负数结转、没有可发放业绩单的销售（含离职销售）也要生成台账，否则扣回无法落地
		appendAdjustmentOnlyAggregates(aggregates, payTimeBefore);
		int generated = 0;
		for (SalesPayoutAggregateVO aggregate : aggregates) {
			String staffId = aggregate.getSalesStaffId();
			if (staffId == null || staffId.isBlank()) {
				continue;
			}
			Long exists = this.baseMapper.selectCount(Wrappers.<SalesPayout>lambdaQuery()
				.eq(SalesPayout::getSalesStaffId, staffId)
				.eq(SalesPayout::getPayoutMonth, month));
			if (exists != null && exists > 0) {
				log.info("发放台账已存在，幂等跳过 staffId={}, payoutMonth={}", staffId, month);
				continue;
			}
			try {
				createPayout(month, aggregate, payTimeBefore);
				generated++;
			}
			catch (DuplicateKeyException e) {
				log.info("并发生成发放台账，幂等跳过 staffId={}, payoutMonth={}", staffId, month);
			}
		}
		log.info("生成销售发放台账完成 payoutMonth={}, generated={}", month, generated);
		return generated;
	}

	/**
	 * 汇总待结转的退款负数调整，把不在业绩单聚合结果中的销售补充为聚合行。
	 * @param aggregates 业绩单聚合行
	 * @param createTimeBefore 调整创建时间上限（不含），阻止之后产生的退款冲减被提前结转到旧月份
	 */
	private void appendAdjustmentOnlyAggregates(List<SalesPayoutAggregateVO> aggregates,
			LocalDateTime createTimeBefore) {
		List<SalesPayoutAdjustment> pendingAdjustments = salesPayoutAdjustmentService.listPending(createTimeBefore);
		if (pendingAdjustments == null || pendingAdjustments.isEmpty()) {
			return;
		}
		Map<String, String> staffNameMap = new LinkedHashMap<>();
		for (SalesPayoutAdjustment adjustment : pendingAdjustments) {
			String staffId = adjustment.getSalesStaffId();
			if (staffId == null || staffId.isBlank()) {
				continue;
			}
			staffNameMap.putIfAbsent(staffId, adjustment.getStaffName());
		}
		Set<String> aggregatedStaffIds = aggregates.stream()
			.map(SalesPayoutAggregateVO::getSalesStaffId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		for (Map.Entry<String, String> entry : staffNameMap.entrySet()) {
			if (aggregatedStaffIds.contains(entry.getKey())) {
				continue;
			}
			SalesPayoutAggregateVO aggregate = new SalesPayoutAggregateVO();
			aggregate.setSalesStaffId(entry.getKey());
			aggregate.setStaffName(entry.getValue());
			aggregate.setOrderCount(0);
			aggregate.setTradeAmount(BigDecimal.ZERO);
			aggregate.setCommissionAmount(BigDecimal.ZERO);
			aggregates.add(aggregate);
		}
	}

	private void createPayout(String month, SalesPayoutAggregateVO aggregate, LocalDateTime payTimeBefore) {
		BigDecimal commissionAmount = defaultZero(aggregate.getCommissionAmount());
		List<SalesPayoutAdjustment> adjustments = salesPayoutAdjustmentService
			.listPendingByStaff(aggregate.getSalesStaffId(), payTimeBefore);
		BigDecimal refundDeduct = BigDecimal.ZERO;
		for (SalesPayoutAdjustment adjustment : adjustments) {
			if (adjustment.getAmount() != null) {
				refundDeduct = refundDeduct.add(adjustment.getAmount().abs());
			}
		}
		SalesPayout payout = new SalesPayout();
		payout.setSalesStaffId(aggregate.getSalesStaffId());
		payout.setStaffName(aggregate.getStaffName());
		payout.setDeptId(aggregate.getDeptId());
		payout.setPayoutMonth(month);
		payout.setOrderCount(aggregate.getOrderCount() == null ? 0 : aggregate.getOrderCount());
		payout.setTradeAmount(defaultZero(aggregate.getTradeAmount()));
		payout.setCommissionAmount(commissionAmount);
		payout.setRefundDeductAmount(refundDeduct);
		payout.setPayableAmount(commissionAmount.subtract(refundDeduct));
		payout.setStatus(SalesPayoutStatusEnum.STATUS_0.getCode());
		if (!this.save(payout)) {
			throw new ArynBusinessException("发放台账保存失败");
		}
		int assigned = salesOrderService.assignPayout(aggregate.getSalesStaffId(), payout.getId(), payTimeBefore);
		salesPayoutAdjustmentService.settleAdjustments(adjustments, payout.getId());
		// 聚合与入批之间可能发生并发退款/并发入批，按实际入批订单重算一次，保证台账金额与批次明细一致
		recalcPendingPayout(payout.getId());
		log.info("生成发放台账 staffId={}, payoutMonth={}, orderCount={}, payable={}, orders={}, adjustments={}",
				aggregate.getSalesStaffId(), month, payout.getOrderCount(), payout.getPayableAmount(), assigned,
				adjustments.size());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean recalcPendingPayout(String payoutId) {
		if (payoutId == null || payoutId.isBlank()) {
			return false;
		}
		SalesPayout payout = this.getById(payoutId);
		if (payout == null || !SalesPayoutStatusEnum.STATUS_0.getCode().equals(payout.getStatus())) {
			return false;
		}
		SalesPayoutAggregateVO summary = salesOrderService.summarizePayoutOrders(payoutId);
		payout.setOrderCount(summary.getOrderCount() == null ? 0 : summary.getOrderCount());
		payout.setTradeAmount(defaultZero(summary.getTradeAmount()));
		payout.setCommissionAmount(defaultZero(summary.getCommissionAmount()));
		// 上期退款冲减金额保持不变，实发金额 = 重算后的应发提成 - 冲减
		payout.setPayableAmount(
				defaultZero(payout.getCommissionAmount()).subtract(defaultZero(payout.getRefundDeductAmount())));
		if (!this.updateById(payout)) {
			throw new ArynBusinessException("待确认台账重算失败，请重试");
		}
		log.info("按批次业绩单重算待确认发放台账 payoutId={}, staffId={}, orderCount={}, commission={}, payable={}",
				payoutId, payout.getSalesStaffId(), payout.getOrderCount(), payout.getCommissionAmount(),
				payout.getPayableAmount());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean confirm(SalesPayoutConfirmDTO dto) {
		if (dto == null || dto.getId() == null || dto.getId().isBlank()) {
			throw new ArynBusinessException("台账ID不能为空");
		}
		SalesPayout payout = this.getById(dto.getId());
		if (payout == null) {
			throw new ArynBusinessException("发放台账不存在");
		}
		if (!SalesPayoutStatusEnum.STATUS_0.getCode().equals(payout.getStatus())) {
			throw new ArynBusinessException("该台账已发放，请勿重复确认");
		}
		LocalDateTime now = LocalDateTime.now();
		// 锁序与退款/生成保持一致：先锁批次内订单，再锁台账行，避免 confirm 与退款交叉死锁
		int paidOrders = salesOrderService.markPayoutPaid(payout.getId(), now);
		// 再按批次内业绩单最新退款情况重算台账（此时台账仍为待确认），防止按退款前金额发放
		recalcPendingPayout(payout.getId());
		// 重算会推进台账 version 且期间可能已被并发确认，必须重新读取最新台账再校验/回写
		SalesPayout latest = this.getById(payout.getId());
		if (latest == null) {
			throw new ArynBusinessException("发放台账不存在");
		}
		if (!SalesPayoutStatusEnum.STATUS_0.getCode().equals(latest.getStatus())) {
			throw new ArynBusinessException("该台账已发放，请勿重复确认");
		}
		int expectedOrders = latest.getOrderCount() == null ? 0 : latest.getOrderCount();
		if (paidOrders != expectedOrders) {
			// 批次内业绩单状态与重算后的台账单数不一致，金额已不可信：回滚本次确认，避免按旧金额发放
			log.warn("台账内业绩单状态已变化，回滚确认 payoutId={}, expectedOrders={}, paidOrders={}",
					latest.getId(), expectedOrders, paidOrders);
			throw new ArynBusinessException("台账内业绩单状态已变化，请刷新后重试");
		}
		latest.setStatus(SalesPayoutStatusEnum.STATUS_1.getCode());
		latest.setConfirmTime(now);
		latest.setConfirmBy(currentOperatorName());
		if (dto.getRemark() != null && !dto.getRemark().isBlank()) {
			latest.setRemark(dto.getRemark());
		}
		if (!this.updateById(latest)) {
			throw new ArynBusinessException("台账确认发放失败，请重试");
		}
		log.info("确认发放销售提成台账 payoutId={}, staffId={}, payable={}, paidOrders={}", latest.getId(),
				latest.getSalesStaffId(), latest.getPayableAmount(), paidOrders);
		return true;
	}

	@Override
	public List<SalesPayout> listForExport(SalesPayout query) {
		SalesPayout condition = query == null ? new SalesPayout() : query;
		return this.list(Wrappers.<SalesPayout>lambdaQuery()
			.eq(notBlank(condition.getPayoutMonth()), SalesPayout::getPayoutMonth, condition.getPayoutMonth())
			.eq(notBlank(condition.getSalesStaffId()), SalesPayout::getSalesStaffId,
					condition.getSalesStaffId())
			.eq(notBlank(condition.getStatus()), SalesPayout::getStatus, condition.getStatus())
			.orderByDesc(SalesPayout::getPayoutMonth));
	}

	private String resolvePayoutMonth(String payoutMonth) {
		if (payoutMonth == null || payoutMonth.isBlank()) {
			return YearMonth.now().minusMonths(1).toString();
		}
		try {
			return YearMonth.parse(payoutMonth).toString();
		}
		catch (Exception e) {
			throw new ArynBusinessException("月份格式必须为 yyyy-MM");
		}
	}

	private String currentOperatorName() {
		try {
			ArynUser user = SecurityUtils.getUser();
			return user == null ? null : user.getUsername();
		}
		catch (Exception e) {
			return null;
		}
	}

	private BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
