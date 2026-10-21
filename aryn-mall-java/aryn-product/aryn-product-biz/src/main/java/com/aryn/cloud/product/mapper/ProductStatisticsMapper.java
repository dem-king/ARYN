package com.aryn.cloud.product.mapper;

import com.aryn.cloud.product.api.dto.ProductStatisticsDTO;
import com.aryn.cloud.product.api.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品统计 Mapper
 */
@Mapper
public interface ProductStatisticsMapper {

	/**
	 * 商品销量排行 TOP 10
	 * @return List<ProductSalesRankVO>
	 */
	List<ProductSalesRankVO> getProductSalesTop10();

	/**
	 * 商品概览统计
	 * @return ProductOverviewVO
	 */
	ProductOverviewVO getProductOverview();

	/**
	 * 商品访问趋势
	 * @param dto 统计参数
	 * @param format 时间格式
	 * @return List<ProductVisitTrendVO>
	 */
	List<ProductVisitTrendVO> getProductVisitTrend(@Param("dto") ProductStatisticsDTO dto,
			@Param("format") String format);

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
	List<ProductVisitRankVO> getProductVisitTop10(@Param("dto") ProductStatisticsDTO dto);

	/**
	 * 获取商品好评榜 TOP 10
	 * @param dto 统计参数
	 * @return List<ProductPraiseRankVO>
	 */
	List<ProductPraiseRankVO> getProductPraiseTop10(@Param("dto") ProductStatisticsDTO dto);

	/**
	 * 商品生命周期概览（基于累计销量与库存）
	 * @return ProductLifecycleVO
	 */
	ProductLifecycleVO getProductLifecycle();

	/**
	 * 统计周期内商品访问去重用户数
	 * @param startTime 开始时间
	 * @param endTime 结束时间
	 * @return 访客数
	 */
	Long countVisitors(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

}
