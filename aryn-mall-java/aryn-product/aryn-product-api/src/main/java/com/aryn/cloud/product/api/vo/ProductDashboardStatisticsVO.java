package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 商品侧概览聚合数据。
 */
@Data
@Schema(description = "商品侧概览聚合数据")
public class ProductDashboardStatisticsVO {

	@Schema(description = "商品概览")
	private ProductOverviewVO overview;

	@Schema(description = "访问趋势")
	private List<ProductVisitTrendVO> visitTrend;

	@Schema(description = "浏览排行 TOP 10")
	private List<ProductVisitRankVO> visitTop10;

	@Schema(description = "好评榜 TOP 10")
	private List<ProductPraiseRankVO> praiseTop10;

	@Schema(description = "库存预警 TOP 10")
	private List<ProductLowStockVO> lowStockTop10;

	@Schema(description = "商品生命周期概览")
	private ProductLifecycleVO lifecycle;

}
