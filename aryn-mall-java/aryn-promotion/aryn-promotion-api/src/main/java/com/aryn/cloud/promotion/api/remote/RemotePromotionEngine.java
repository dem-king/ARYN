package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;

/**
 * 统一营销引擎远程接口。
 *
 * <p>时序：结算 preview → 下单 reserve → 支付成功 confirm → 取消/超时 release；
 * reserve/confirm/release 以订单+活动维度幂等。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface RemotePromotionEngine {

	/**
	 * 试算：不改任何状态，返回命中活动与优惠结果。
	 */
	PromotionCalculationVO preview(PromotionContextDTO context);

	/**
	 * 锁定：下单时预留优惠名额/规则版本；重复调用返回原结果（幂等）。
	 */
	PromotionCalculationVO reserve(PromotionContextDTO context);

	/**
	 * 确认：支付成功后固化锁定记录（幂等）。
	 */
	void confirm(String tenantId, String orderId);

	/**
	 * 释放：取消/超时/退款时释放锁定（幂等）。
	 * @param reason CANCEL/TIMEOUT/REFUND
	 */
	void release(String tenantId, String orderId, String reason);

}
