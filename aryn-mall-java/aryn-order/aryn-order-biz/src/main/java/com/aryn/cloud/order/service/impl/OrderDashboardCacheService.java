package com.aryn.cloud.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.OrderDashboardStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 概览聚合接口短缓存：TTL 60 秒，Redis 不可用时直接回退数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDashboardCacheService {

	private static final String KEY_PREFIX = "dashboard:order:";

	private static final Duration TTL = Duration.ofSeconds(60);

	private final StringRedisTemplate stringRedisTemplate;

	public OrderDashboardStatisticsVO getOrLoad(OrderStatisticsDTO dto,
			Supplier<OrderDashboardStatisticsVO> loader) {
		String key = buildKey(dto);
		if (key == null) {
			return loader.get();
		}

		try {
			String cached = stringRedisTemplate.opsForValue().get(key);
			if (StringUtils.hasText(cached)) {
				return JSON.parseObject(cached, OrderDashboardStatisticsVO.class);
			}
		}
		catch (Exception ex) {
			log.warn("读取概览缓存失败，回退数据库", ex);
		}

		OrderDashboardStatisticsVO data = loader.get();
		try {
			stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(data), TTL);
		}
		catch (Exception ex) {
			log.warn("写入概览缓存失败", ex);
		}
		return data;
	}

	private String buildKey(OrderStatisticsDTO dto) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return null;
		}
		return String.join(":", KEY_PREFIX + tenantId,
				String.valueOf(dto.getStartTime()), String.valueOf(dto.getEndTime()),
				String.valueOf(dto.getCompareStartTime()), String.valueOf(dto.getCompareEndTime()),
				Boolean.toString(Boolean.TRUE.equals(dto.getIncludeUserAnalytics())));
	}

}
