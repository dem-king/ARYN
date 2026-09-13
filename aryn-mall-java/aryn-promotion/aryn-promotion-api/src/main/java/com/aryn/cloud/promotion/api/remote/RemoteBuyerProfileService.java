package com.aryn.cloud.promotion.api.remote;

import java.util.List;

/**
 * 买家画像远程接口（常购专属价等按用户历史行为匹配的活动使用）。
 *
 * <p>接口定义在 promotion-api（避免与 order-api 循环依赖），
 * 实现由 aryn-order-biz 提供（订单数据归属方）。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface RemoteBuyerProfileService {

	/**
	 * 查询用户近 90 天购买过的 SKU ID 集合（常购判定）。
	 */
	List<String> frequentSkuIds(String tenantId, String userId);

}
