package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ProductStatisticsDTO;
import com.aryn.cloud.product.api.vo.*;

import java.time.LocalDateTime;
import java.util.List;

public interface IProductStatisticsService {

	/**
	 * 统计周期内商品访问去重用户数
	 * @param startTime 开始时间
	 * @param endTime 结束时间
	 * @return 访客数
	 */
	Long countVisitors(LocalDateTime startTime, LocalDateTime endTime);

	/**
	 * 商品侧概览聚合数据
	 * @param dto 统计参数
	 * @return ProductDashboardStatisticsVO
	 */
	ProductDashboardStatisticsVO getProductDashboard(ProductStatisticsDTO dto);

	/**
	 * 获取商品销量 TOP 10
	 * @return List<ProductSalesRankVO>
	 */
	List<ProductSalesRankVO> getProductSalesTop10();

	/**
	 * 获取商品概览统计
	 * @return ProductOverviewVO
	 */
	ProductOverviewVO getProductOverview();

	/**
	 * 获取商品访问趋势 (PV/UV)
	 * @param dto 统计参数
	 * @return List<ProductVisitTrendVO>
	 */
	List<ProductVisitTrendVO> getProductVisitTrend(ProductStatisticsDTO dto);

	/**
	 * 获取库存预警商品 (库存<10) TOP 10
	 * @return List<ProductLowStockVO>
	 */
	List<ProductLowStockVO> getLowStockTop10();

	/**
	 * 获取商品浏览排行 TOP 10 (PV/UV)
	 * @param dto 统计参数
	 * @return List<ProductVisitRankVO>
	 */
	List<ProductVisitRankVO> getProductVisitTop10(ProductStatisticsDTO dto);

	/**
	 * 获取商品好评榜 TOP 10
	 * @param dto 统计参数
	 * @return List<ProductPraiseRankVO>
	 */
	List<ProductPraiseRankVO> getProductPraiseTop10(ProductStatisticsDTO dto);

}
