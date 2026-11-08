package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.vo.GoodsCardTagVO;

import java.util.List;

/**
 * 商品卡营销标签聚合服务（C 端列表卡面）。
 *
 * @author aryn
 * @since 2026/11/08
 */
public interface IGoodsCardTagService {

	/**
	 * 批量查询 SPU 卡面标签（新人价 + 秒杀价）。
	 *
	 * <p>可选登录：登录用户携带新人资格判定（不合格不下发），未登录按潜在新人口径
	 * 下发（与免登楼层一致）。任何失败降级为空列表——标签属增强展示，不影响商品卡主流程。
	 *
	 * @param spuIds SPU ID 集合（去重、截断到单次上限）
	 * @return 命中的标签（只包含至少命中一项的 SPU）
	 */
	List<GoodsCardTagVO> cardTags(List<String> spuIds);

}
