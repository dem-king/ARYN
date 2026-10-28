package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.vo.MemberActivityGoodsVO;

/**
 * 会员专区自动数据源服务（C 端装修多 Tab）。
 *
 * <p>与会员价查价服务（{@link IMemberPriceActivityService}）解耦：本服务只负责
 * 「会员价活动 → 商品 SPU 列表」的搬运与保序去重，不下发价格、不含用户维度数据、免登。
 *
 * @author aryn
 * @since 2026/10/25
 */
public interface IMemberActivityGoodsService {

	/**
	 * 取会员价活动商品对应的 SPU 列表。
	 *
	 * @param activityId 指定活动ID（装修时固定活动）；null/空表示取当前进行中的会员价活动
	 *                   （已发布 + 时间窗内，priority desc、create_time desc 第一个）
	 * @return 活动元信息与去重保序的 SPU 列表；无可用活动 / 活动非会员价类型 / 无商品 / 查询异常返回 null
	 */
	MemberActivityGoodsVO getActivityGoods(String activityId);

}
