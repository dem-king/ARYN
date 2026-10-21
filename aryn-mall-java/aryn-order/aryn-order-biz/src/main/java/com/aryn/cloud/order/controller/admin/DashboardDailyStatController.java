package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.vo.DailyReconcileVO;
import com.aryn.cloud.order.service.IDailyUserOrderStatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 用户日订单汇总对账。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/order/statistics/daily")
@Tag(description = "dailystatistics", name = "日汇总对账")
public class DashboardDailyStatController {

	private final IDailyUserOrderStatService dailyUserOrderStatService;

	@GetMapping("/reconcile")
	@Operation(summary = "原始明细与用户日汇总对账")
	public Result<DailyReconcileVO> reconcile(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return Result.success(dailyUserOrderStatService.reconcile(startDate, endDate));
	}

}
