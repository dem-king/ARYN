package com.aryn.cloud.promotion.api.remote;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 专区最终价查询：读取系统托管活动 rules 中已算好的价格（含时间分层）。
 *
 * <p>C 端展示必须与结算同源：商品域不再自行推算分层折扣，直接取影子活动最终价。
 *
 * @author aryn
 * @since 2026/11/03
 */
public interface RemoteMallZonePriceService {

	/**
	 * 出清专区 SKU 最终价（系统活动 rules 已含分层折扣与到期过滤）。
	 *
	 * @param tenantId 租户 ID
	 * @param skuIds 待查询 SKU
	 * @return skuId → 出清价；无有效价的 SKU 不返回
	 */
	Map<String, BigDecimal> clearancePrices(String tenantId, List<String> skuIds);

}
