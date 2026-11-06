package com.aryn.cloud.promotion.util;

import com.alibaba.excel.EasyExcel;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesPayout;
import com.aryn.cloud.promotion.api.enums.SalesOrderStatusEnum;
import com.aryn.cloud.promotion.api.enums.SalesPayoutStatusEnum;
import com.aryn.cloud.promotion.api.vo.SalesReportVO;
import jakarta.servlet.http.HttpServletResponse;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 销售域 Excel 导出（管理端业绩单/发放台账/业绩报表）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public final class SalesExcelExporter {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private static final List<List<String>> ORDER_HEAD = List.of(List.of("订单号"), List.of("买家ID"),
			List.of("归属销售"), List.of("部门ID"), List.of("支付时间"), List.of("实付金额（含运费）"), List.of("运费"),
			List.of("提成基数（不含运费）"), List.of("提成比例"), List.of("提成金额"), List.of("已退基数"),
			List.of("已扣回提成"), List.of("状态"), List.of("发放批次"));

	private static final List<List<String>> PAYOUT_HEAD = List.of(List.of("发放月份"), List.of("销售ID"),
			List.of("销售姓名"), List.of("部门ID"), List.of("业绩单数"), List.of("成交额"), List.of("应发提成"),
			List.of("上期退款冲减"), List.of("实发金额"), List.of("状态"), List.of("确认人"), List.of("确认时间"),
			List.of("备注/凭证号"));

	private static final List<List<String>> REPORT_HEAD = List.of(List.of("销售ID"), List.of("销售姓名"),
			List.of("部门"), List.of("单数"), List.of("成交额"), List.of("退款额"), List.of("应发提成"),
			List.of("已发放提成"));

	private SalesExcelExporter() {
	}

	/**
	 * 统一写入 Excel 导出响应头（xlsx Content-Type + 中文文件名）。
	 * @param response 响应
	 * @param baseName 文件名前缀（不含时间戳与扩展名）
	 */
	public static void prepareResponse(HttpServletResponse response, String baseName) {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String fileName = baseName + "_" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
		response.setHeader("Content-Disposition",
				"attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
	}

	/**
	 * 业绩单导出。
	 */
	public static void writeOrders(List<SalesOrder> orders, OutputStream outputStream) {
		List<List<Object>> rows = new ArrayList<>(orders.size());
		for (SalesOrder order : orders) {
			List<Object> row = new ArrayList<>(14);
			row.add(order.getOrderNo());
			row.add(order.getBuyerUserId());
			row.add(order.getStaffName());
			row.add(order.getDeptId());
			row.add(formatTime(order.getPayTime()));
			row.add(defaultZero(order.getPaymentAmount()));
			row.add(defaultZero(order.getFreightAmount()));
			row.add(defaultZero(order.getCommissionBase()));
			row.add(order.getCommissionRate());
			row.add(defaultZero(order.getCommissionAmount()));
			row.add(defaultZero(order.getRefundedBase()));
			row.add(defaultZero(order.getRefundedCommission()));
			row.add(SalesOrderStatusEnum.getValue(order.getStatus()));
			row.add(order.getPayoutId());
			rows.add(row);
		}
		EasyExcel.write(outputStream).head(ORDER_HEAD).sheet("销售业绩单").doWrite(rows);
	}

	/**
	 * 月度发放台账导出。
	 */
	public static void writePayouts(List<SalesPayout> payouts, OutputStream outputStream) {
		List<List<Object>> rows = new ArrayList<>(payouts.size());
		for (SalesPayout payout : payouts) {
			List<Object> row = new ArrayList<>(13);
			row.add(payout.getPayoutMonth());
			row.add(payout.getSalesStaffId());
			row.add(payout.getStaffName());
			row.add(payout.getDeptId());
			row.add(payout.getOrderCount());
			row.add(defaultZero(payout.getTradeAmount()));
			row.add(defaultZero(payout.getCommissionAmount()));
			row.add(defaultZero(payout.getRefundDeductAmount()));
			row.add(defaultZero(payout.getPayableAmount()));
			row.add(SalesPayoutStatusEnum.getValue(payout.getStatus()));
			row.add(payout.getConfirmBy());
			row.add(formatTime(payout.getConfirmTime()));
			row.add(payout.getRemark());
			rows.add(row);
		}
		EasyExcel.write(outputStream).head(PAYOUT_HEAD).sheet("发放台账").doWrite(rows);
	}

	/**
	 * 业绩报表导出。
	 */
	public static void writeReport(List<SalesReportVO> reportRows, OutputStream outputStream) {
		List<List<Object>> rows = new ArrayList<>(reportRows.size());
		for (SalesReportVO report : reportRows) {
			List<Object> row = new ArrayList<>(8);
			row.add(report.getSalesStaffId());
			row.add(report.getStaffName());
			row.add(report.getDeptName());
			row.add(report.getOrderCount());
			row.add(defaultZero(report.getTradeAmount()));
			row.add(defaultZero(report.getRefundAmount()));
			row.add(defaultZero(report.getCommissionAmount()));
			row.add(defaultZero(report.getPaidAmount()));
			rows.add(row);
		}
		EasyExcel.write(outputStream).head(REPORT_HEAD).sheet("业绩报表").doWrite(rows);
	}

	private static String formatTime(LocalDateTime time) {
		return time == null ? null : DATE_TIME_FORMATTER.format(time);
	}

	private static BigDecimal defaultZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

}
