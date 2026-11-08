package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.mapper.SpuSalesStatMapper;
import com.aryn.cloud.order.service.ISpuSalesStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * SPU 社证统计实现：聚合写入走自定义 upsert（幂等），读取取最新一期。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuSalesStatServiceImpl implements ISpuSalesStatService {

	/** 单次查询 SPU 上限（卡面标签页级批量，超出忽略——社证缺失只影响文案不影响交易） */
	private static final int MAX_QUERY_SPUS = 200;

	private final SpuSalesStatMapper spuSalesStatMapper;

	@Override
	public int aggregateStats(String tenantId, LocalDate statDate, int days) {
		if (!StringUtils.hasText(tenantId) || statDate == null || days <= 0) {
			return 0;
		}
		return spuSalesStatMapper.upsertSpuStats(tenantId, statDate, days);
	}

	@Override
	public List<GoodsSpuSalesStatDTO> latestStats(String tenantId, List<String> spuIds) {
		if (!StringUtils.hasText(tenantId) || CollectionUtils.isEmpty(spuIds)) {
			return List.of();
		}
		List<String> wanted = spuIds.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.distinct()
			.limit(MAX_QUERY_SPUS)
			.toList();
		if (wanted.isEmpty()) {
			return List.of();
		}
		List<GoodsSpuSalesStatDTO> stats = spuSalesStatMapper.selectLatestStats(tenantId, wanted);
		return stats == null ? List.of() : stats;
	}

}
