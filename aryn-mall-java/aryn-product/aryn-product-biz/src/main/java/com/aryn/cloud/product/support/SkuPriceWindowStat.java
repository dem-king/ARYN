package com.aryn.cloud.product.support;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 近 N 天价格窗口统计（「近14天最低价」判定的查询出参，内部不出域）。
 *
 * <p>由 {@code GoodsSkuPriceDailyMapper#selectWindowStats} 一次 GROUP BY 聚合得出：
 * (tenant_id, sku_id, stat_date) 唯一索引下每 SKU 窗口内仅十几行，页级 20 SPU
 * 一次聚合成本可忽略。todayPrice 取当日快照价（Job 未跑/当天不在售时为 null）。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
public class SkuPriceWindowStat {

	/** SKU ID */
	private String skuId;

	/** 窗口内最低快照价（含当日行） */
	private BigDecimal minPrice;

	/** 窗口内出现过的不同价格个数（≥2 才视为「发生过变价」） */
	private Long priceKinds;

	/** 当日快照价（=当日非个性化到手价）；null=当日无快照行 */
	private BigDecimal todayPrice;

}
