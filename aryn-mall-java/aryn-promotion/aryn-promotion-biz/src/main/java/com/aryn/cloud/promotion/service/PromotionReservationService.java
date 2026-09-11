package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;

/**
 * 营销锁定服务：下单预留、支付确认、取消释放，全部以订单+活动维度幂等。
 *
 * @author aryn
 * @since 2026/9/12
 */
public interface PromotionReservationService {

	/**
	 * 锁定：试算并写入锁定记录；已存在锁定时直接返回原计算结果。
	 */
	PromotionCalculationVO reserve(PromotionContextDTO context);

	/**
	 * 确认锁定（支付成功）。
	 */
	void confirm(String tenantId, String orderId);

	/**
	 * 释放锁定（取消/超时/退款）。
	 * @param reason CANCEL/TIMEOUT/REFUND
	 */
	void release(String tenantId, String orderId, String reason);

}
