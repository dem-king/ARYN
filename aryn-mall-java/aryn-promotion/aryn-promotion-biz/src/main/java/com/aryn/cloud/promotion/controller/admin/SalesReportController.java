package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.vo.SalesReportVO;
import com.aryn.cloud.promotion.api.vo.SalesTrendVO;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.util.SalesExcelExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 管理端-销售业绩报表
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/report")
@Tag(name = "销售业绩报表", description = "按销售/部门/月份汇总与导出")
public class SalesReportController {

	private final ISalesOrderService salesOrderService;

	@Operation(summary = "业绩汇总")
	@SaCheckPermission("promotion:salesreport:page")
	@GetMapping("/summary")
	public Result<List<SalesReportVO>> summary(@RequestParam(required = false) String payoutMonth,
			@RequestParam(required = false) String salesStaffId,
			@RequestParam(required = false) String deptId) {
		return Result.success(salesOrderService.reportSummary(payoutMonth, salesStaffId, deptId));
	}

	@SysLog("销售业绩报表导出")
	@Operation(summary = "业绩报表导出")
	@SaCheckPermission("promotion:salesreport:export")
	@GetMapping("/export")
	public void export(HttpServletResponse response, @RequestParam(required = false) String payoutMonth,
			@RequestParam(required = false) String salesStaffId,
			@RequestParam(required = false) String deptId) throws IOException {
		List<SalesReportVO> rows = salesOrderService.reportSummary(payoutMonth, salesStaffId, deptId);
		SalesExcelExporter.prepareResponse(response, "销售业绩报表");
		SalesExcelExporter.writeReport(rows, response.getOutputStream());
	}

	@Operation(summary = "业绩趋势")
	@SaCheckPermission("promotion:salesreport:page")
	@GetMapping("/trend")
	public Result<List<SalesTrendVO>> trend(@RequestParam(required = false) String salesStaffId,
			@RequestParam(required = false, defaultValue = "6") Integer months) {
		return Result.success(salesOrderService.reportTrend(salesStaffId, months == null ? 6 : months));
	}

}
