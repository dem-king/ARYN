package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.vo.FirstOrderEligibilityVO;
import com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO;

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

	/**
	 * 新人资格判定：本租户内未成功支付过订单，且注册时间在 registerWithinDays 天内。
	 *
	 * <p>口径见 {@link NewbieEligibilityVO}；任何异常按非新人返回（fail-closed），
	 * 由调用侧决定是否告警，不阻断下单主流程。
	 *
	 * @param tenantId 租户 ID
	 * @param userId 用户 ID
	 * @param registerWithinDays 注册时效（天）；null/&lt;=0 表示不限时效
	 */
	NewbieEligibilityVO checkNewbieEligibility(String tenantId, String userId, Integer registerWithinDays);

	/**
	 * 个人新人资格判定：与 {@link #checkNewbieEligibility} 同口径，但只统计
	 * {@code purchase_scene=1}（含历史未回填的空场景）的个人订单，避免船供/企业单误伤个人新人。
	 */
	NewbieEligibilityVO checkPersonalNewbieEligibility(String tenantId, String userId, Integer registerWithinDays);

	/**
	 * 船供首单资格：该船在本租户内是否从未成功支付过 {@code purchase_scene=2} 的订单。
	 *
	 * @param tenantId 租户 ID
	 * @param vesselId 船舶 ID
	 */
	FirstOrderEligibilityVO checkShipFirstOrderEligibility(String tenantId, String vesselId);

	/**
	 * 企业首单资格：采购主体已审核通过且未停用，并且该主体在本租户内从未成功支付过
	 * {@code purchase_scene=3} 的订单。
	 *
	 * @param tenantId 租户 ID
	 * @param buyerOrgId 采购主体 ID
	 */
	FirstOrderEligibilityVO checkBuyerOrgFirstOrderEligibility(String tenantId, String buyerOrgId);

}
