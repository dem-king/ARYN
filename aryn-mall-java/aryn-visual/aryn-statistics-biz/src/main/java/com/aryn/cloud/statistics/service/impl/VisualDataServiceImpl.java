package com.aryn.cloud.statistics.service.impl;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.remote.RemoteOrderStatisticsService;
import com.aryn.cloud.order.api.vo.CategoryRankVO;
import com.aryn.cloud.order.api.vo.OrderTradeStatisticsVO;
import com.aryn.cloud.order.api.vo.OrderTrendVO;
import com.aryn.cloud.statistics.service.IVisualDataService;
import com.aryn.cloud.statistics.vo.VisualCategoryRankVO;
import com.aryn.cloud.statistics.vo.VisualOrderTrendVO;
import com.aryn.cloud.statistics.vo.VisualOverviewVO;
import com.aryn.cloud.statistics.vo.VisualUserFunnelVO;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsApi;
import com.aryn.cloud.user.api.vo.UserFunnelVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 大屏数据聚合服务实现
 * <p>
 * 聚合 order-biz 和 user-biz 的统计数据，通过 Redis 缓存加速访问。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualDataServiceImpl implements IVisualDataService {

	private final RedisTemplate<String, Object> redisTemplate;

	@DubboReference
	private RemoteOrderStatisticsService remoteOrderStatisticsService;

	@DubboReference
	private RemoteUserStatisticsApi remoteUserStatisticsApi;

	private static final String KEY_OVERVIEW = "visual:overview";

	private static final String KEY_ORDER_TREND = "visual:order-trend";

	private static final String KEY_CATEGORY_RANK = "visual:category-rank";

	private static final String KEY_USER_FUNNEL = "visual:user-funnel";

	private static final long CACHE_TTL_MINUTES = 5L;

	@Override
	@SuppressWarnings("unchecked")
	public VisualOverviewVO getOverview() {
		Object cached = redisTemplate.opsForValue().get(KEY_OVERVIEW);
		if (cached != null) {
			log.debug("命中缓存: {}", KEY_OVERVIEW);
			return (VisualOverviewVO) cached;
		}

		log.info("缓存未命中，查询大屏概览数据");
		LocalDateTime endTime = LocalDateTime.now();
		LocalDateTime startTime = endTime.minusDays(1);

		OrderStatisticsDTO orderDto = new OrderStatisticsDTO();
		orderDto.setStartTime(startTime);
		orderDto.setEndTime(endTime);

		OrderTradeStatisticsVO tradeStats = remoteOrderStatisticsService.getOrderOverview(orderDto);
		Long onlineUserCount = remoteUserStatisticsApi.getOnlineUserCount();

		VisualOverviewVO vo = new VisualOverviewVO();
		vo.setGmv(tradeStats != null && tradeStats.getGmv() != null ? tradeStats.getGmv() : BigDecimal.ZERO);
		vo.setOrderCount(tradeStats != null && tradeStats.getPayOrderCount() != null ? tradeStats.getPayOrderCount()
				: 0);
		vo.setOnlineUserCount(onlineUserCount != null ? onlineUserCount : 0L);

		// 计算转化率: 下单用户数 / 总注册用户数
		UserStatisticsDTO userDto = new UserStatisticsDTO();
		userDto.setStartTime(startTime);
		userDto.setEndTime(endTime);
		UserFunnelVO funnel = remoteUserStatisticsApi.getUserFunnel(userDto);
		long registerCount = funnel != null && funnel.getRegisterCount() != null ? funnel.getRegisterCount() : 0L;
		long orderUserCount = tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount().longValue() : 0L;
		if (registerCount > 0) {
			vo.setConversionRate(BigDecimal.valueOf(orderUserCount)
				.divide(BigDecimal.valueOf(registerCount), 4, RoundingMode.HALF_UP));
		}
		else {
			vo.setConversionRate(BigDecimal.ZERO);
		}

		redisTemplate.opsForValue().set(KEY_OVERVIEW, vo, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
		return vo;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<VisualOrderTrendVO> getOrderTrend(String range) {
		String cacheKey = KEY_ORDER_TREND + ":" + range;
		Object cached = redisTemplate.opsForValue().get(cacheKey);
		if (cached != null) {
			log.debug("命中缓存: {}", cacheKey);
			return (List<VisualOrderTrendVO>) cached;
		}

		log.info("缓存未命中，查询订单趋势数据, range={}", range);
		int days = "30d".equals(range) ? 30 : 7;
		LocalDateTime endTime = LocalDateTime.now();
		LocalDateTime startTime = endTime.minusDays(days);

		OrderStatisticsDTO orderDto = new OrderStatisticsDTO();
		orderDto.setStartTime(startTime);
		orderDto.setEndTime(endTime);

		List<OrderTrendVO> trendList = remoteOrderStatisticsService.getOrderTrend(orderDto);
		if (trendList == null) {
			trendList = new ArrayList<>();
		}

		List<VisualOrderTrendVO> result = new ArrayList<>();
		for (OrderTrendVO trend : trendList) {
			VisualOrderTrendVO vo = new VisualOrderTrendVO();
			vo.setTimePoint(trend.getTimePoint());
			vo.setGmv(trend.getGmv() != null ? trend.getGmv() : BigDecimal.ZERO);
			vo.setPayOrderCount(trend.getPayOrderCount() != null ? trend.getPayOrderCount() : 0);
			result.add(vo);
		}

		redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<VisualCategoryRankVO> getCategoryRank() {
		Object cached = redisTemplate.opsForValue().get(KEY_CATEGORY_RANK);
		if (cached != null) {
			log.debug("命中缓存: {}", KEY_CATEGORY_RANK);
			return (List<VisualCategoryRankVO>) cached;
		}

		log.info("缓存未命中，查询品类排行数据");
		LocalDateTime endTime = LocalDateTime.now();
		LocalDateTime startTime = endTime.minusDays(30);

		OrderStatisticsDTO orderDto = new OrderStatisticsDTO();
		orderDto.setStartTime(startTime);
		orderDto.setEndTime(endTime);

		List<CategoryRankVO> rankList = remoteOrderStatisticsService.getCategoryRank(orderDto);
		if (rankList == null) {
			rankList = new ArrayList<>();
		}

		List<VisualCategoryRankVO> result = new ArrayList<>();
		for (CategoryRankVO rank : rankList) {
			VisualCategoryRankVO vo = new VisualCategoryRankVO();
			vo.setCategoryName(rank.getCategoryName());
			vo.setSalesCount(rank.getSalesCount() != null ? rank.getSalesCount() : 0);
			vo.setSalesAmount(rank.getSalesAmount() != null ? rank.getSalesAmount() : BigDecimal.ZERO);
			result.add(vo);
		}

		redisTemplate.opsForValue().set(KEY_CATEGORY_RANK, result, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public VisualUserFunnelVO getUserFunnel() {
		Object cached = redisTemplate.opsForValue().get(KEY_USER_FUNNEL);
		if (cached != null) {
			log.debug("命中缓存: {}", KEY_USER_FUNNEL);
			return (VisualUserFunnelVO) cached;
		}

		log.info("缓存未命中，查询用户漏斗数据");
		LocalDateTime endTime = LocalDateTime.now();
		LocalDateTime startTime = endTime.minusDays(30);

		// 1. 从用户服务获取注册数
		UserStatisticsDTO userDto = new UserStatisticsDTO();
		userDto.setStartTime(startTime);
		userDto.setEndTime(endTime);
		UserFunnelVO userFunnel = remoteUserStatisticsApi.getUserFunnel(userDto);

		// 2. 从订单服务获取下单用户数和复购用户数
		OrderStatisticsDTO orderDto = new OrderStatisticsDTO();
		orderDto.setStartTime(startTime);
		orderDto.setEndTime(endTime);
		OrderTradeStatisticsVO tradeStats = remoteOrderStatisticsService.getOrderOverview(orderDto);

		VisualUserFunnelVO vo = new VisualUserFunnelVO();
		vo.setRegisterCount(userFunnel != null && userFunnel.getRegisterCount() != null
				? userFunnel.getRegisterCount() : 0L);
		vo.setOrderCount(tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount().longValue() : 0L);

		// 复购用户数: 从订单统计中获取复购率，反算复购用户数
		Integer repurchaseUserCount = remoteOrderStatisticsService.getRepurchaseUserCount(orderDto);
		vo.setRepurchaseCount(repurchaseUserCount != null ? repurchaseUserCount.longValue() : 0L);

		redisTemplate.opsForValue().set(KEY_USER_FUNNEL, vo, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
		return vo;
	}

	/**
	 * 预热所有大屏缓存数据 (供定时任务调用)
	 */
	public void preloadAllCache() {
		log.info("开始预热大屏缓存数据");
		try {
			// 清除旧缓存
			redisTemplate.delete(KEY_OVERVIEW);
			redisTemplate.delete(KEY_ORDER_TREND + ":7d");
			redisTemplate.delete(KEY_ORDER_TREND + ":30d");
			redisTemplate.delete(KEY_CATEGORY_RANK);
			redisTemplate.delete(KEY_USER_FUNNEL);

			// 重新加载
			getOverview();
			getOrderTrend("7d");
			getOrderTrend("30d");
			getCategoryRank();
			getUserFunnel();

			log.info("大屏缓存数据预热完成");
		}
		catch (Exception e) {
			log.error("大屏缓存数据预热失败", e);
		}
	}

}