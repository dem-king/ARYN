package com.aryn.cloud.product.service;

import java.time.LocalDate;

/**
 * SKU 每日价格快照服务（「近14天最低价」判定底表写入）。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface IGoodsSkuPriceDailyService {

	/**
	 * 对指定租户的全部在售 SKU 写当日快照行。
	 *
	 * <p>价 = MIN(标价, 出清生效价, 秒杀价)；出清价走专区同源口径、秒杀价走远程
	 * 批量接口（失败降级仅本地价）。价格是时点数据无法回填历史，调用方不传
	 * 回填参数——Job 重跑即用当前价覆盖当日行。
	 *
	 * @param tenantId 租户 ID（缺失直接返回 0）
	 * @param statDate 快照日
	 * @return 写入（含覆盖）行数
	 */
	int snapshotDaily(String tenantId, LocalDate statDate);

}
