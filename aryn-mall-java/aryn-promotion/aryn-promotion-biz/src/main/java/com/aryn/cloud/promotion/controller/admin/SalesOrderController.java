package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesOrderQueryDTO;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.vo.SalesOrderVO;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.util.SalesExcelExporter;
import com.aryn.cloud.promotion.vo.SalesOrderStatisticsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.beans.PropertyEditorSupport;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理端-销售业绩单
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order")
@Tag(name = "销售业绩单", description = "销售业绩明细查询、汇总与导出")
public class SalesOrderController {

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final ISalesOrderService salesOrderService;

	/**
	 * 查询条件时间字段复用 {@link SalesOrderQueryDTO} 绑定，注册编辑器兼容：
	 * 前端当前格式 yyyy-MM-dd HH:mm:ss、ISO 8601（yyyy-MM-dd'T'HH:mm:ss）与纯日期 yyyy-MM-dd。
	 * 纯日期保持原查询口径：开始时间补 00:00:00，结束时间补到当日 23:59:59。
	 */
	@InitBinder
	public void initBinder(WebDataBinder binder) {
		binder.registerCustomEditor(LocalDateTime.class, "payTimeStart", new QueryDateTimeEditor(false));
		binder.registerCustomEditor(LocalDateTime.class, "payTimeEnd", new QueryDateTimeEditor(true));
	}

	@Operation(summary = "业绩单分页")
	@SaCheckPermission("promotion:salesorder:page")
	@GetMapping("/page")
	public Result<IPage<SalesOrderVO>> page(Page<SalesOrder> page, SalesOrderQueryDTO query) {
		return Result.success(salesOrderService.selectAdminPage(page, query));
	}

	@SysLog("销售业绩单导出")
	@Operation(summary = "业绩单导出")
	@SaCheckPermission("promotion:salesorder:export")
	@GetMapping("/export")
	public void export(HttpServletResponse response, SalesOrderQueryDTO query) throws IOException {
		List<SalesOrder> orders = salesOrderService.listForExport(query);
		SalesExcelExporter.prepareResponse(response, "销售业绩单");
		SalesExcelExporter.writeOrders(orders, response.getOutputStream());
	}

	@Operation(summary = "业绩单汇总卡片")
	@SaCheckPermission("promotion:salesorder:page")
	@GetMapping("/statistics")
	public Result<SalesOrderStatisticsVO> statistics(SalesOrderQueryDTO query) {
		return Result.success(salesOrderService.statistics(query));
	}

	/**
	 * 查询时间参数编辑器：纯日期按开始/结束语义补齐时分秒，其余按 yyyy-MM-dd HH:mm:ss 或 ISO 8601 解析。
	 */
	private static final class QueryDateTimeEditor extends PropertyEditorSupport {

		/** 是否结束时间（纯日期需补到当日 23:59:59） */
		private final boolean endOfDay;

		private QueryDateTimeEditor(boolean endOfDay) {
			this.endOfDay = endOfDay;
		}

		@Override
		public void setAsText(String text) {
			if (text == null || text.isBlank()) {
				setValue(null);
				return;
			}
			String value = text.trim();
			try {
				if (value.length() == 10) {
					LocalDate date = LocalDate.parse(value);
					setValue(endOfDay ? date.plusDays(1).atStartOfDay().minusSeconds(1) : date.atStartOfDay());
					return;
				}
				setValue(LocalDateTime.parse(value, DATE_TIME_FORMATTER));
			}
			catch (Exception e) {
				try {
					setValue(LocalDateTime.parse(value));
				}
				catch (Exception ignored) {
					throw new ArynBusinessException("时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss");
				}
			}
		}

	}

}
