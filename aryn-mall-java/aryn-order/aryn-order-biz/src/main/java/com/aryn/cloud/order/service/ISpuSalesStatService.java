package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;

import java.time.LocalDate;
import java.util.List;

/**
 * SPU 社证统计服务（T+1 聚合与读取）。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface ISpuSalesStatService {

	/**
	 * 聚合写入指定租户、指定快照日的近 days 天社证统计（uk 冲突走覆盖，可重复执行）。
	 *
	 * @param tenantId 租户 ID（空直接返回 0）
	 * @param statDate 统计快照日（窗口截至当日）
	 * @param days     统计窗口天数（须 &gt; 0）
	 * @return 影响行数（含覆盖更新）
	 */
	int aggregateStats(String tenantId, LocalDate statDate, int days);

	/**
	 * 取各 SPU 的最新一期统计（每 SPU 取 stat_date 最大行；入参去重、截断到单次上限）。
	 */
	List<GoodsSpuSalesStatDTO> latestStats(String tenantId, List<String> spuIds);

}
