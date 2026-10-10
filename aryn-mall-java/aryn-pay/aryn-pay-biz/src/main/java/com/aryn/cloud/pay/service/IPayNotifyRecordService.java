
package com.aryn.cloud.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayNotifyRecord;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付订单
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
public interface IPayNotifyRecordService extends IService<PayNotifyRecord> {

	/**
	 * 微信支付通知
	 * @param tenantId
	 * @param terminalType
	 * @param notifyData
	 * @return
	 */
	String wxPayNotify(String tenantId, String terminalType, String notifyData);

	/**
	 * 支付宝支付通知
	 * @param tenantId
	 * @param terminalType
	 * @param request
	 * @return
	 */
	String aliPayNotify(String tenantId, String terminalType, HttpServletRequest request);

	/**
	 * 微信退款通知
	 * @param tenantId
	 * @param notifyData
	 * @return
	 */
	String wxPayRefundNotify(String tenantId, String terminalType, String notifyData);

	/**
	 * 主动向渠道查询支付结果并落账（支付回调未达时由业务侧触发的权威核对）。
	 *
	 * <p>支付回调经「渠道 → 回调接口 → MQ」异步投递，存在延迟或丢失的可能：
	 * 用户已付款但业务侧仍是待支付。业务侧在自家单据未完成时调用本方法，
	 * 由支付域向渠道取真实结论，而不是让调用方靠轮询次数猜测。
	 *
	 * <p>确认成功时与回调走同一条落账路径（幂等推进支付单 → 记录通知 →
	 * 广播 pay-notify-topic），下游业务无需区分「回调」与「查单」。
	 * 目前支持微信交易（WX_*）；其它渠道无查单能力时返回 paid=false。
	 *
	 * @param tenantId 租户ID
	 * @param outTradeNo 商户订单号
	 * @return 核对结果；渠道未确认、无查单能力或查单失败时 paid=false
	 */
	PaySettlementResult queryAndSettlePayOrder(String tenantId, String outTradeNo);

}
