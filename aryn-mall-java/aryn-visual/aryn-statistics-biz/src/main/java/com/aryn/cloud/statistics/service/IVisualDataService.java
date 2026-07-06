package com.aryn.cloud.statistics.service;

import com.aryn.cloud.statistics.vo.VisualCategoryRankVO;
import com.aryn.cloud.statistics.vo.VisualOrderTrendVO;
import com.aryn.cloud.statistics.vo.VisualOverviewVO;
import com.aryn.cloud.statistics.vo.VisualUserFunnelVO;

import java.util.List;

/**
 * 大屏数据聚合服务
 */
public interface IVisualDataService {

	/**
	 * 获取大屏概览数据 (GMV/订单数/在线用户/转化率)
	 * @return VisualOverviewVO
	 */
	VisualOverviewVO getOverview();

	/**
	 * 获取订单趋势 (近7天/30天)
	 * @param range 时间范围 (7d/30d)
	 * @return List<VisualOrderTrendVO>
	 */
	List<VisualOrderTrendVO> getOrderTrend(String range);

	/**
	 * 获取品类销售排行 Top10
	 * @return List<VisualCategoryRankVO>
	 */
	List<VisualCategoryRankVO> getCategoryRank();

	/**
	 * 获取用户漏斗 (注册→下单→复购)
	 * @return VisualUserFunnelVO
	 */
	VisualUserFunnelVO getUserFunnel();

}