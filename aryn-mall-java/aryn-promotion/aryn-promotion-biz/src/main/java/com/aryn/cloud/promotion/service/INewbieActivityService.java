package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.vo.AppNewbieFloorVO;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;

import java.util.List;

/**
 * 新人专享楼层服务（C 端展示）。
 *
 * @author aryn
 * @since 2026/10/13
 */
public interface INewbieActivityService {

	/**
	 * 取当前进行中的新人专享活动（含新人价商品列表）；无进行中活动返回 null。
	 *
	 * <p>固定返回活动配置价，不做按人出价；传 userId 时附带当前用户的资格判定，
	 * 展示端据此对已登录老客隐藏楼层（未登录不下发资格）。商品条数按首页楼层的展示上限截断。
	 *
	 * @param userId 当前登录用户 ID；null/空表示未登录，资格字段不下发
	 */
	AppNewbieFloorVO getCurrentFloor(String userId);

	/**
	 * 取当前进行中的新人专享活动，并指定商品返回条数。
	 *
	 * <p>独立的新人专享页需要看到活动内全部商品，不能沿用首页楼层的 20 条上限；
	 * 入参在实现内收敛到 [1, 200]，避免列表页请求把响应体无限放大。
	 *
	 * @param limit 期望返回的商品条数
	 * @param userId 当前登录用户 ID；null/空表示未登录，资格字段不下发
	 */
	AppNewbieFloorVO getCurrentFloor(int limit, String userId);

	/**
	 * 批量查询指定 SKU 的新人价（商详 / 购物车等已聚焦商品的页面用）。
	 *
	 * <p>与楼层接口同源（同一个「进行中的新人价活动」），但只返回请求 SKU 中
	 * 被活动覆盖的部分；可选登录：传 userId 时附带当前用户的资格判定结果，
	 * 页面据此决定展示新人价还是原价，不收窄任何写接口的鉴权。
	 *
	 * @param skuIds 待查 SKU ID（空则直接返回 null，不发查询）
	 * @param userId 当前登录用户 ID；null/空表示未登录，资格字段不下发
	 * @return 命中的活动与商品；无进行中活动、或请求 SKU 全未命中时返回 null
	 */
	AppNewbiePriceVO getSkuPrices(List<String> skuIds, String userId);

}
