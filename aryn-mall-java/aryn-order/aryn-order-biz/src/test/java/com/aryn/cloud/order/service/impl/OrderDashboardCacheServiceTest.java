package com.aryn.cloud.order.service.impl;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.OrderDashboardStatisticsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDashboardCacheServiceTest {

	@Mock
	private StringRedisTemplate stringRedisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	private OrderDashboardCacheService cacheService;

	@BeforeEach
	void setUp() {
		cacheService = new OrderDashboardCacheService(stringRedisTemplate);
		when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
		ArynTenantContextHolder.setTenantId("tenant-1");
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsCachedValueWhenPresent() {
		OrderDashboardStatisticsVO cached = new OrderDashboardStatisticsVO();
		when(valueOperations.get(anyString())).thenReturn(JSON.toJSONString(cached));

		OrderDashboardStatisticsVO result = cacheService.getOrLoad(buildDto(),
				() -> new OrderDashboardStatisticsVO());

		assertThat(result).isNotNull();
	}

	@Test
	void loadsAndCachesOnMiss() {
		when(valueOperations.get(anyString())).thenReturn(null);
		OrderDashboardStatisticsVO loaded = new OrderDashboardStatisticsVO();
		Supplier<OrderDashboardStatisticsVO> loader = () -> loaded;

		assertThat(cacheService.getOrLoad(buildDto(), loader)).isSameAs(loaded);
		verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
	}

	@Test
	void fallsBackWhenRedisReadFails() {
		when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));
		OrderDashboardStatisticsVO loaded = new OrderDashboardStatisticsVO();

		assertThat(cacheService.getOrLoad(buildDto(), () -> loaded)).isSameAs(loaded);
	}

	private OrderStatisticsDTO buildDto() {
		OrderStatisticsDTO dto = new OrderStatisticsDTO();
		dto.setStartTime(LocalDateTime.of(2026, 10, 1, 0, 0));
		dto.setEndTime(LocalDateTime.of(2026, 10, 20, 23, 59, 59));
		dto.setIncludeUserAnalytics(true);
		return dto;
	}

}
