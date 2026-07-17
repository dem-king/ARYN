package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;

import java.util.List;

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
	 * 释放一笔到期的待结算佣金。
	 *
	 * @param distributionOrderId 分销订单ID
	 * @return true=本次完成释放，false=订单不存在或已处理
	 */
	Boolean settlePendingOrder(String distributionOrderId);

	/**
	 * 退款回退佣金：按退款金额比例回退已结算佣金
	 *
	 * @param orderId 业务订单ID
	 * @param refundNo 退款业务号
	 * @param refundAmount 含运费的退款总额
	 * @param refundBaseAmount 不含运费的退款佣金基数
	 * @return true=本次完成回退，false=重复退款或无佣金可回退
	 */
	Boolean refundCommission(String orderId, String refundNo, java.math.BigDecimal refundAmount,
			java.math.BigDecimal refundBaseAmount);

	/**
	 * 重放当前租户尚未关联到佣金订单的退款记录。
	 *
	 * @return 本次成功应用的退款记录数
	 */
	List<String> listPendingRefundIds();

	Boolean replayPendingRefund(String refundRecordId);

}
