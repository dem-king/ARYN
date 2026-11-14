package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportRowVO;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportVO;
import com.aryn.cloud.promotion.service.FirstOrderReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 首单核销报表（P2）：统计船供/企业首单的锁定、核销、释放、让利与月预算预警。
 *
 * @author aryn
 * @since 2026/11/13
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/first-order-report")
@Tag(description = "promotion-first-order-report", name = "首单核销报表")
public class FirstOrderReportController {

	private final FirstOrderReportService firstOrderReportService;

	@Operation(summary = "首单核销报表")
	@SaCheckPermission("promotion:firstorderreport:page")
	@GetMapping("/page")
	public Result<FirstOrderReportVO> page(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
		return Result.success(firstOrderReportService.report(ArynTenantContextHolder.getTenantId(),
			beginTime, endTime));
	}

	@Operation(summary = "首单预算预警")
	@SaCheckPermission("promotion:firstorderreport:page")
	@GetMapping("/alerts")
	public Result<List<FirstOrderReportRowVO>> alerts(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
		return Result.success(firstOrderReportService.alerts(ArynTenantContextHolder.getTenantId(),
			beginTime, endTime));
	}

	@Operation(summary = "导出首单核销报表")
	@SysLog("导出首单核销报表")
	@SaCheckPermission("promotion:firstorderreport:export")
	@GetMapping("/export")
	public void export(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
			HttpServletResponse response) throws IOException {
		FirstOrderReportVO report = firstOrderReportService.report(
			ArynTenantContextHolder.getTenantId(), beginTime, endTime);
		String fileName = "首单核销报表_"
			+ DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".csv";
		response.setContentType("text/csv;charset=UTF-8");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setHeader("Content-Disposition",
			"attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));

		try (OutputStream output = response.getOutputStream()) {
			output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
			writeLine(output, "活动ID,活动名称,主体类型,锁定笔数,已核销笔数,已释放笔数,去重主体数,"
				+ "已核销让利金额,月预算,本月已占用,月预算使用率(%),预警");
			for (FirstOrderReportRowVO row : report.getRows()) {
				writeLine(output, String.join(",",
					csv(row.getActivityId()), csv(row.getActivityName()), subjectType(row.getSubjectType()),
					str(row.getLockedCount()), str(row.getConfirmedCount()), str(row.getReleasedCount()),
					str(row.getSubjectCount()), money(row.getDiscountAmount()), money(row.getMonthlyBudget()),
					money(row.getMonthlyUsed()), percent(row.getUsageRate()),
					Boolean.TRUE.equals(row.getAlert()) ? "是" : "否"));
			}
		}
	}

	private static String subjectType(String type) {
		if ("1".equals(type)) {
			return "船";
		}
		if ("2".equals(type)) {
			return "采购主体";
		}
		return type == null ? "" : type;
	}

	private static String str(Long value) {
		return value == null ? "0" : value.toString();
	}

	private static String money(BigDecimal value) {
		return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	private static String percent(BigDecimal value) {
		return value == null ? "" : value.toPlainString();
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		String safe = value.replace("\"", "\"\"");
		if (safe.startsWith("=") || safe.startsWith("+") || safe.startsWith("-") || safe.startsWith("@")) {
			safe = "'" + safe;
		}
		return "\"" + safe + "\"";
	}

	private static void writeLine(OutputStream output, String line) throws IOException {
		output.write((line + "\n").getBytes(StandardCharsets.UTF_8));
	}

}
