package com.aryn.cloud.statistics.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.statistics.service.IVisualDataService;
import com.aryn.cloud.statistics.vo.VisualCategoryRankVO;
import com.aryn.cloud.statistics.vo.VisualOrderTrendVO;
import com.aryn.cloud.statistics.vo.VisualOverviewVO;
import com.aryn.cloud.statistics.vo.VisualUserFunnelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 大屏数据聚合接口 (管理端)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/visual")
@Tag(description = "visualdata", name = "数据大屏")
public class VisualDataController {

	private final IVisualDataService visualDataService;

	@GetMapping("/overview")
	@Operation(summary = "实时概览数据 (GMV/订单数/在线用户/转化率)")
	public Result<VisualOverviewVO> getOverview() {
		return Result.success(visualDataService.getOverview());
	}

	@GetMapping("/order-trend")
	@Operation(summary = "订单趋势 (近7天/30天每日订单数/金额)")
	public Result<List<VisualOrderTrendVO>> getOrderTrend(
			@RequestParam(defaultValue = "7d") String range) {
		return Result.success(visualDataService.getOrderTrend(range));
	}

	@GetMapping("/category-rank")
	@Operation(summary = "品类销售排行 Top10")
	public Result<List<VisualCategoryRankVO>> getCategoryRank() {
		return Result.success(visualDataService.getCategoryRank());
	}

	@GetMapping("/user-funnel")
	@Operation(summary = "注册→下单→复购漏斗")
	public Result<VisualUserFunnelVO> getUserFunnel() {
		return Result.success(visualDataService.getUserFunnel());
	}

}