package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.vo.AppMemberPriceVO;

import java.util.List;

/**
 * 会员价查询服务（C 端展示）。
 *
 * <p>会员价活动（promotion_activity type=10）与新人专享价同族，只是资格维度为会员等级：
 * 本接口只做展示口径的查询，资格最终仍在下单链路（引擎 preview）硬校验。
 *
 * @author aryn
 * @since 2026/10/25
 */
public interface IMemberPriceActivityService {

	/**
	 * 批量查询指定 SKU 的会员价（商详 / 购物车等已确定具体 SKU 的页面）。
	 *
	 * <p>可选登录：未登录只下发活动配置价、资格字段为 null（展示端按营销引导处理）；
	 * 携带 userId 时附带当前用户等级与资格判定（fail-closed：无等级/远程异常按不合格）。
	 *
	 * @param skuIds 待查 SKU ID（空则直接返回 null，不发查询）
	 * @param userId 当前登录用户 ID；null/空表示未登录，等级与资格字段不下发
	 * @return 命中的会员价商品；无进行中活动、或请求 SKU 全未命中时返回 null
	 */
	AppMemberPriceVO getSkuPrices(List<String> skuIds, String userId);

	/**
	 * 批量查询指定 SPU 下 SKU 的会员价（商品列表卡片场景）。
	 *
	 * <p>列表卡片只知道 SPU（卡片价是 SPU 聚合价），本方法以活动配置的 SKU 为候选集，
	 * 经既有商品批量接口回填 spuId 与当前售价后只保留请求 SPU 下的 SKU。
	 *
	 * <p><b>同一 SPU 可能返回多条 item</b>（不同规格各自配了会员价），服务端不做挑选，
	 * 由 C 端按卡片展示价对应的 SKU 精确匹配（后端挑选会在多规格场景给出与卡片不一致的价格）。
	 *
	 * <p>可选登录与 fail-closed 口径同 {@link #getSkuPrices}；未登录时 eligible=null，
	 * 登录但无等级/不达标/远程异常时 eligible=false。
	 *
	 * @param spuIds 待查 SPU ID（空则直接返回 null，不发查询；超限静默截断）
	 * @param userId 当前登录用户 ID；null/空表示未登录，等级与资格字段不下发
	 * @return 命中的会员价商品；无进行中活动、或请求 SPU 全未命中时返回 null
	 */
	AppMemberPriceVO getSpuPrices(List<String> spuIds, String userId);

}
