package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;

/**
 * 统一营销计算引擎。
 *
 * <p>同一活动类型命中多个活动时取优惠最大者（同类取最优）；
 * 阶梯价与整船优惠跨类型按固定顺序叠加（阶梯价先改基价，整船优惠在商品金额上计算）。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface PromotionEngineService {

	/**
	 * 试算：不改任何状态。
	 */
	PromotionCalculationVO preview(PromotionContextDTO context);

}
