package com.aryn.cloud.product.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.dto.ProductStatisticsDTO;
import com.aryn.cloud.product.api.vo.*;
import com.aryn.cloud.product.service.IProductStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/product/statistics")
@Tag(description = "productstatistics", name = "商品统计")
public class ProductStatisticsController {

	private final IProductStatisticsService productStatisticsService;

	@GetMapping("/sales/top10")
	@Operation(summary = "商品销量TOP10")
	public Result<List<ProductSalesRankVO>> getProductSalesTop10() {
		return Result.success(productStatisticsService.getProductSalesTop10());
	}

	@GetMapping("/overview")
	@Operation(summary = "商品概览")
	public Result<ProductOverviewVO> getProductOverview() {
		return Result.success(productStatisticsService.getProductOverview());
	}

	@GetMapping("/visit/trend")
	@Operation(summary = "商品访问趋势 (PV/UV)")
	public Result<List<ProductVisitTrendVO>> getProductVisitTrend(ProductStatisticsDTO dto) {
		return Result.success(productStatisticsService.getProductVisitTrend(dto));
	}

	@GetMapping("/stock/low")
	@Operation(summary = "库存预警商品 (库存<10)")
	public Result<List<ProductLowStockVO>> getLowStockTop10() {
		return Result.success(productStatisticsService.getLowStockTop10());
	}

	@GetMapping("/visit/top10")
	@Operation(summary = "商品浏览排行 TOP 10 (PV/UV)")
	public Result<List<ProductVisitRankVO>> getProductVisitTop10(ProductStatisticsDTO dto) {
		return Result.success(productStatisticsService.getProductVisitTop10(dto));
	}

	@GetMapping("/praise/top10")
	@Operation(summary = "商品好评榜 TOP 10")
	public Result<List<ProductPraiseRankVO>> getProductPraiseTop10(ProductStatisticsDTO dto) {
		return Result.success(productStatisticsService.getProductPraiseTop10(dto));
	}

}
