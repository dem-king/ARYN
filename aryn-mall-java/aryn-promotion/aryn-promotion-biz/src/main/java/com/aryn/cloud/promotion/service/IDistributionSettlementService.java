package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;

/**
 * 分销结算服务
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
public interface IDistributionSettlementService {

	/**
	 * 订单结算：归因分销关系并计算佣金
	 *
	 * @param dto 结算请求
	 * @return true=本次执行了结算，false=已存在跳过或无需结算
	 */
	Boolean settleOrder(DistributionSettleDTO dto);

	/**
	 * 退款回退佣金：按退款金额比例回退已结算佣金
	 *
	 * @param orderId 业务订单ID
	 * @param refundAmount 退款金额
	 */
	void refundCommission(String orderId, java.math.BigDecimal refundAmount);

}
