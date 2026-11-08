package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.mapper.SpuSalesStatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SPU 社证统计服务的入参守卫与截断契约。
 *
 * <p>Job 与远程两条入口共用本服务：聚合入参不合法直接 0 行（不打库），
 * 读取入参去重/截断到单次上限（超出忽略——社证缺失只影响文案不影响交易）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SPU社证统计服务")
class SpuSalesStatServiceImplTest {

	@Mock
	private SpuSalesStatMapper spuSalesStatMapper;

	@InjectMocks
	private SpuSalesStatServiceImpl service;

	@Test
	@DisplayName("聚合入参不合法直接返回 0 且不打库")
	void aggregateGuards() {
		assertThat(service.aggregateStats(null, LocalDate.now(), 30)).isZero();
		assertThat(service.aggregateStats(" ", LocalDate.now(), 30)).isZero();
		assertThat(service.aggregateStats("t1", null, 30)).isZero();
		assertThat(service.aggregateStats("t1", LocalDate.now(), 0)).isZero();
		verifyNoInteractions(spuSalesStatMapper);
	}

	@Test
	@DisplayName("聚合正常路径透传影响行数")
	void aggregatePassesThrough() {
		when(spuSalesStatMapper.upsertSpuStats("t1", LocalDate.of(2026, 11, 7), 30)).thenReturn(12);

		assertThat(service.aggregateStats("t1", LocalDate.of(2026, 11, 7), 30)).isEqualTo(12);
	}

	@Test
	@DisplayName("读取入参去重并截断到单次上限")
	void latestStatsDeduplicatedAndClamped() {
		when(spuSalesStatMapper.selectLatestStats(anyString(), anyList())).thenReturn(List.of());

		// 205 个去重值 + 3 个重复 = 208 入参 → 去重 205 → 截断 200
		List<String> input = new ArrayList<>(IntStream.rangeClosed(1, 205).mapToObj(i -> "spu-" + i).toList());
		input.addAll(List.of("spu-1", "spu-2", "spu-3"));

		service.latestStats("t1", input);

		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(spuSalesStatMapper).selectLatestStats(anyString(), captor.capture());
		assertThat(captor.getValue()).hasSize(200);
		assertThat(captor.getValue()).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("空入参不打库")
	void latestStatsGuards() {
		assertThat(service.latestStats(null, List.of("spu-1"))).isEmpty();
		assertThat(service.latestStats("t1", List.of())).isEmpty();
		assertThat(service.latestStats("t1", List.of(" ", ""))).isEmpty();
		verifyNoInteractions(spuSalesStatMapper);
	}

	@Test
	@DisplayName("mapper 空结果兜底为空列表")
	void latestStatsNullSafe() {
		when(spuSalesStatMapper.selectLatestStats(anyString(), anyList())).thenReturn(null);

		assertThat(service.latestStats("t1", List.of("spu-1"))).isEmpty();
	}

}
