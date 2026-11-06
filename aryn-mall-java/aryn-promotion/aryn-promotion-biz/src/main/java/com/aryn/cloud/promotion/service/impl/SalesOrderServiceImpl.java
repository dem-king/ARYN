package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.convert.Convert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.dto.SalesOrderQueryDTO;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesOrderStatusEnum;
import com.aryn.cloud.promotion.api.vo.AppSalesOrderVO;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;
import com.aryn.cloud.promotion.api.vo.SalesReportVO;
import com.aryn.cloud.promotion.api.vo.SalesTrendVO;
import com.aryn.cloud.promotion.mapper.SalesOrderMapper;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.promotion.vo.SalesOrderStatisticsVO;
import com.aryn.cloud.promotion.vo.SalesPayoutAggregateVO;
import com.aryn.cloud.promotion.vo.SalesWorkbenchStatsVO;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售业绩单服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements ISalesOrderService {

	private final ISalesStaffService salesStaffService;

	private final SalesUserEnricher salesUserEnricher;

	@Override
	public IPage<SalesOrderVO> selectAdminPage(Page<SalesOrder> page, SalesOrderQueryDTO query) {
		IPage<SalesOrder> orderPage = this.page(page, buildLambdaQuery(query));
		return convertWithNickname(orderPage);
	}

	@Override
	public List<SalesOrder> listForExport(SalesOrderQueryDTO query) {
		return this.list(buildLambdaQuery(query));
	}

	@Override
	public SalesOrderStatisticsVO statistics(SalesOrderQueryDTO query) {
		QueryWrapper<SalesOrder> wrapper = buildQueryWrapper(query);
		wrapper.select("COUNT(*) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount",
				"IFNULL(SUM(refunded_base), 0) AS refund_amount");
		SalesOrderStatisticsVO vo = new SalesOrderStatisticsVO();
		vo.setOrderCount(0L);
		vo.setTradeAmount(BigDecimal.ZERO);
		vo.setCommissionAmount(BigDecimal.ZERO);
		vo.setRefundAmount(BigDecimal.ZERO);
		List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			vo.setOrderCount(Convert.toLong(row.get("order_count"), 0L));
			vo.setTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setCommissionAmount(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
			vo.setRefundAmount(Convert.toBigDecimal(row.get("refund_amount"), BigDecimal.ZERO));
		}
		return vo;
	}

	@Override
	public List<SalesReportVO> reportSummary(String payoutMonth, String salesStaffId, String deptId) {
		QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
		wrapper.select("sales_staff_id",
				"MAX(staff_name) AS staff_name",
				"MAX(dept_id) AS dept_id",
				"COUNT(*) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(refunded_base), 0) AS refund_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount",
				"IFNULL(SUM(CASE WHEN status = '2' THEN commission_amount - refunded_commission ELSE 0 END), 0)"
						+ " AS paid_amount")
			.eq(notBlank(salesStaffId), "sales_staff_id", salesStaffId)
			.eq(notBlank(deptId), "dept_id", deptId)
			.groupBy("sales_staff_id");
		applyMonthRange(wrapper, payoutMonth);
		List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
		if (rows.isEmpty()) {
			return Collections.emptyList();
		}
		Map<String, SalesStaff> staffMap = salesStaffService.getStaffMap(rows.stream()
			.map(row -> Convert.toStr(row.get("sales_staff_id")))
			.filter(java.util.Objects::nonNull)
			.toList());
		List<SalesReportVO> result = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			SalesReportVO vo = new SalesReportVO();
			String staffId = Convert.toStr(row.get("sales_staff_id"));
			vo.setSalesStaffId(staffId);
			vo.setStaffName(Convert.toStr(row.get("staff_name")));
			vo.setOrderCount(Convert.toInt(row.get("order_count"), 0));
			vo.setTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setRefundAmount(Convert.toBigDecimal(row.get("refund_amount"), BigDecimal.ZERO));
			vo.setCommissionAmount(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
			vo.setPaidAmount(Convert.toBigDecimal(row.get("paid_amount"), BigDecimal.ZERO));
			SalesStaff staff = staffMap.get(staffId);
			if (staff != null) {
				vo.setDeptName(staff.getDeptName());
				if (vo.getStaffName() == null) {
					vo.setStaffName(staff.getStaffName());
				}
			}
			result.add(vo);
		}
		return result;
	}

	@Override
	public List<SalesTrendVO> reportTrend(String salesStaffId, int months) {
		int monthCount = Math.min(Math.max(months, 1), 24);
		YearMonth end = YearMonth.now();
		YearMonth start = end.minusMonths(monthCount - 1L);
		QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
		wrapper.select("DATE_FORMAT(pay_time, '%Y-%m') AS month",
				"COUNT(*) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount")
			.eq(notBlank(salesStaffId), "sales_staff_id", salesStaffId)
			.ge("pay_time", start.atDay(1).atStartOfDay())
			.groupBy("DATE_FORMAT(pay_time, '%Y-%m')");
		Map<String, SalesTrendVO> trendMap = new LinkedHashMap<>();
		for (Map<String, Object> row : this.baseMapper.selectMaps(wrapper)) {
			SalesTrendVO vo = new SalesTrendVO();
			vo.setMonth(Convert.toStr(row.get("month")));
			vo.setOrderCount(Convert.toInt(row.get("order_count"), 0));
			vo.setTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setCommissionAmount(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
			trendMap.put(vo.getMonth(), vo);
		}
		List<SalesTrendVO> result = new ArrayList<>(monthCount);
		for (int i = 0; i < monthCount; i++) {
			String month = start.plusMonths(i).toString();
			SalesTrendVO vo = trendMap.get(month);
			if (vo == null) {
				vo = new SalesTrendVO();
				vo.setMonth(month);
				vo.setOrderCount(0);
				vo.setTradeAmount(BigDecimal.ZERO);
				vo.setCommissionAmount(BigDecimal.ZERO);
			}
			result.add(vo);
		}
		return result;
	}

	@Override
	public IPage<AppSalesOrderVO> selectAppPage(Page<SalesOrder> page, String salesStaffId) {
		IPage<SalesOrder> orderPage = this.page(page, Wrappers.<SalesOrder>lambdaQuery()
			.eq(SalesOrder::getSalesStaffId, salesStaffId)
			.orderByDesc(SalesOrder::getPayTime));
		return orderPage.convert(order -> {
			AppSalesOrderVO vo = new AppSalesOrderVO();
			vo.setId(order.getId());
			vo.setOrderNo(order.getOrderNo());
			vo.setPayTime(order.getPayTime());
			vo.setCommissionBase(order.getCommissionBase());
			vo.setCommissionAmount(order.getCommissionAmount());
			vo.setStatus(order.getStatus());
			return vo;
		});
	}

	@Override
	public SalesOrder getByBizOrderId(String bizOrderId) {
		if (bizOrderId == null || bizOrderId.isBlank()) {
			return null;
		}
		return this.getOne(Wrappers.<SalesOrder>lambdaQuery()
			.eq(SalesOrder::getBizOrderId, bizOrderId)
			.last("limit 1"));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveIdempotent(SalesOrder order) {
		try {
			return this.save(order);
		}
		catch (DuplicateKeyException e) {
			log.info("销售业绩单唯一键冲突，按幂等跳过 bizOrderId={}", order.getBizOrderId());
			return false;
		}
	}

	@Override
	public List<SalesPayoutAggregateVO> aggregatePayoutOrders(LocalDateTime payTimeBefore) {
		QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
		wrapper.select("sales_staff_id",
				"MAX(staff_name) AS staff_name",
				"MAX(dept_id) AS dept_id",
				"COUNT(*) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount")
			.eq("status", SalesOrderStatusEnum.STATUS_1.getCode())
			.isNull("payout_id")
			.lt("pay_time", payTimeBefore)
			.groupBy("sales_staff_id");
		List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
		List<SalesPayoutAggregateVO> result = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			SalesPayoutAggregateVO vo = new SalesPayoutAggregateVO();
			vo.setSalesStaffId(Convert.toStr(row.get("sales_staff_id")));
			vo.setStaffName(Convert.toStr(row.get("staff_name")));
			vo.setDeptId(Convert.toStr(row.get("dept_id")));
			vo.setOrderCount(Convert.toInt(row.get("order_count"), 0));
			vo.setTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setCommissionAmount(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
			result.add(vo);
		}
		return result;
	}

	@Override
	public boolean existsUnbatchedPayoutOrders(LocalDateTime payTimeBefore) {
		Long count = this.baseMapper.selectCount(Wrappers.<SalesOrder>lambdaQuery()
			.eq(SalesOrder::getStatus, SalesOrderStatusEnum.STATUS_1.getCode())
			.isNull(SalesOrder::getPayoutId)
			.lt(SalesOrder::getPayTime, payTimeBefore));
		return count != null && count > 0;
	}

	@Override
	public SalesPayoutAggregateVO summarizePayoutOrders(String payoutId) {
		QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
		// status 为 char(2)，全额退款单不计入单数；成交额/提成按扣减退款后的净额汇总
		wrapper.select("IFNULL(SUM(CASE WHEN status <> '3' THEN 1 ELSE 0 END), 0) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount")
			.eq("payout_id", payoutId);
		SalesPayoutAggregateVO vo = new SalesPayoutAggregateVO();
		vo.setOrderCount(0);
		vo.setTradeAmount(BigDecimal.ZERO);
		vo.setCommissionAmount(BigDecimal.ZERO);
		List<Map<String, Object>> rows = this.baseMapper.selectMaps(wrapper);
		if (!rows.isEmpty()) {
			Map<String, Object> row = rows.get(0);
			vo.setOrderCount(Convert.toInt(row.get("order_count"), 0));
			vo.setTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setCommissionAmount(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
		}
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int assignPayout(String salesStaffId, String payoutId, LocalDateTime payTimeBefore) {
		SalesOrder update = new SalesOrder();
		update.setPayoutId(payoutId);
		// 批量入批不经过 @Version 乐观锁，必须手工推进 version；
		// 否则并发退款事务按旧 version 仍能 updateById 命中已入批订单，跳过重算造成部分退款超付
		return this.baseMapper.update(update, Wrappers.<SalesOrder>lambdaUpdate()
			.setSql("version = COALESCE(version, 0) + 1")
			.eq(SalesOrder::getSalesStaffId, salesStaffId)
			.eq(SalesOrder::getStatus, SalesOrderStatusEnum.STATUS_1.getCode())
			.isNull(SalesOrder::getPayoutId)
			.lt(SalesOrder::getPayTime, payTimeBefore));
	}

	@Override
	public IPage<SalesOrderVO> selectPayoutOrderPage(Page<SalesOrder> page, String payoutId) {
		IPage<SalesOrder> orderPage = this.page(page, Wrappers.<SalesOrder>lambdaQuery()
			.eq(SalesOrder::getPayoutId, payoutId)
			.orderByDesc(SalesOrder::getPayTime));
		return convertWithNickname(orderPage);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int markPayoutPaid(String payoutId, LocalDateTime settleTime) {
		SalesOrder update = new SalesOrder();
		update.setStatus(SalesOrderStatusEnum.STATUS_2.getCode());
		update.setSettleTime(settleTime == null ? LocalDateTime.now() : settleTime);
		// 批量更新不经过 @Version 乐观锁，必须手工推进 version；
		// 否则并发退款事务按旧 version 仍能 updateById 命中已发放订单，把 status 从 2 回写成 1 造成多发
		return this.baseMapper.update(update, Wrappers.<SalesOrder>lambdaUpdate()
			.setSql("version = COALESCE(version, 0) + 1")
			.eq(SalesOrder::getPayoutId, payoutId)
			.eq(SalesOrder::getStatus, SalesOrderStatusEnum.STATUS_1.getCode()));
	}

	@Override
	public SalesWorkbenchStatsVO workbenchStats(String salesStaffId) {
		LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
		LocalDateTime monthEnd = monthStart.plusMonths(1);
		QueryWrapper<SalesOrder> monthWrapper = new QueryWrapper<>();
		monthWrapper.select("COUNT(*) AS order_count",
				"IFNULL(SUM(commission_base - refunded_base), 0) AS trade_amount",
				"IFNULL(SUM(commission_amount - refunded_commission), 0) AS commission_amount")
			.eq("sales_staff_id", salesStaffId)
			.ne("status", SalesOrderStatusEnum.STATUS_3.getCode())
			.ge("pay_time", monthStart)
			.lt("pay_time", monthEnd);
		QueryWrapper<SalesOrder> totalWrapper = new QueryWrapper<>();
		totalWrapper.select("IFNULL(SUM(commission_amount - refunded_commission), 0) AS total_commission")
			.eq("sales_staff_id", salesStaffId)
			.ne("status", SalesOrderStatusEnum.STATUS_3.getCode());

		SalesWorkbenchStatsVO vo = new SalesWorkbenchStatsVO();
		vo.setMonthOrderCount(0);
		vo.setMonthTradeAmount(BigDecimal.ZERO);
		vo.setMonthCommission(BigDecimal.ZERO);
		vo.setTotalCommission(BigDecimal.ZERO);
		List<Map<String, Object>> monthRows = this.baseMapper.selectMaps(monthWrapper);
		if (!monthRows.isEmpty()) {
			Map<String, Object> row = monthRows.get(0);
			vo.setMonthOrderCount(Convert.toInt(row.get("order_count"), 0));
			vo.setMonthTradeAmount(Convert.toBigDecimal(row.get("trade_amount"), BigDecimal.ZERO));
			vo.setMonthCommission(Convert.toBigDecimal(row.get("commission_amount"), BigDecimal.ZERO));
		}
		List<Map<String, Object>> totalRows = this.baseMapper.selectMaps(totalWrapper);
		if (!totalRows.isEmpty()) {
			vo.setTotalCommission(
					Convert.toBigDecimal(totalRows.get(0).get("total_commission"), BigDecimal.ZERO));
		}
		return vo;
	}

	private LambdaQueryWrapper<SalesOrder> buildLambdaQuery(SalesOrderQueryDTO query) {
		SalesOrderQueryDTO condition = query == null ? new SalesOrderQueryDTO() : query;
		return Wrappers.<SalesOrder>lambdaQuery()
			.eq(notBlank(condition.getSalesStaffId()), SalesOrder::getSalesStaffId, condition.getSalesStaffId())
			.eq(notBlank(condition.getDeptId()), SalesOrder::getDeptId, condition.getDeptId())
			.eq(notBlank(condition.getStatus()), SalesOrder::getStatus, condition.getStatus())
			.eq(notBlank(condition.getPayoutId()), SalesOrder::getPayoutId, condition.getPayoutId())
			.eq(notBlank(condition.getBuyerUserId()), SalesOrder::getBuyerUserId, condition.getBuyerUserId())
			.like(notBlank(condition.getOrderNo()), SalesOrder::getOrderNo, condition.getOrderNo())
			.like(notBlank(condition.getStaffName()), SalesOrder::getStaffName, condition.getStaffName())
			.like(notBlank(condition.getSalesStaffName()), SalesOrder::getStaffName,
					condition.getSalesStaffName())
			.ge(condition.getPayTimeStart() != null, SalesOrder::getPayTime, condition.getPayTimeStart())
			.le(condition.getPayTimeEnd() != null, SalesOrder::getPayTime, condition.getPayTimeEnd())
			.orderByDesc(SalesOrder::getPayTime);
	}

	private QueryWrapper<SalesOrder> buildQueryWrapper(SalesOrderQueryDTO query) {
		SalesOrderQueryDTO condition = query == null ? new SalesOrderQueryDTO() : query;
		QueryWrapper<SalesOrder> wrapper = new QueryWrapper<>();
		wrapper.eq(notBlank(condition.getSalesStaffId()), "sales_staff_id", condition.getSalesStaffId())
			.eq(notBlank(condition.getDeptId()), "dept_id", condition.getDeptId())
			.eq(notBlank(condition.getStatus()), "status", condition.getStatus())
			.eq(notBlank(condition.getPayoutId()), "payout_id", condition.getPayoutId())
			.eq(notBlank(condition.getBuyerUserId()), "buyer_user_id", condition.getBuyerUserId())
			.like(notBlank(condition.getOrderNo()), "order_no", condition.getOrderNo())
			.like(notBlank(condition.getStaffName()), "staff_name", condition.getStaffName())
			.like(notBlank(condition.getSalesStaffName()), "staff_name", condition.getSalesStaffName())
			.ge(condition.getPayTimeStart() != null, "pay_time", condition.getPayTimeStart())
			.le(condition.getPayTimeEnd() != null, "pay_time", condition.getPayTimeEnd());
		return wrapper;
	}

	private void applyMonthRange(QueryWrapper<SalesOrder> wrapper, String payoutMonth) {
		if (payoutMonth == null || payoutMonth.isBlank()) {
			return;
		}
		YearMonth month;
		try {
			month = YearMonth.parse(payoutMonth);
		}
		catch (Exception e) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("月份格式必须为 yyyy-MM");
		}
		wrapper.ge("pay_time", month.atDay(1).atStartOfDay()).lt("pay_time", month.plusMonths(1).atDay(1).atStartOfDay());
	}

	private IPage<SalesOrderVO> convertWithNickname(IPage<SalesOrder> orderPage) {
		Map<String, UserInfoVO> userMap = salesUserEnricher.getUserMap(orderPage.getRecords()
			.stream()
			.map(SalesOrder::getBuyerUserId)
			.toList());
		return orderPage.convert(order -> {
			SalesOrderVO vo = new SalesOrderVO();
			BeanUtils.copyProperties(order, vo);
			UserInfoVO user = userMap.get(order.getBuyerUserId());
			if (user != null) {
				vo.setBuyerNickname(user.getNickname());
			}
			return vo;
		});
	}

	private boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
