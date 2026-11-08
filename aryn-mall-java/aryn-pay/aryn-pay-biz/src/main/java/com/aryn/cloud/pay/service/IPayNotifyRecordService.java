
package com.aryn.cloud.pay.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
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
	 * 旧版微信支付通知入口（仅用于已发出的存量支付迁移窗口）。
	 */
	PayNotifyAck wxPayNotify(String tenantId, String terminalType, String notifyData);

	/**
	 * 旧版支付宝支付通知入口（仅用于已发出的存量支付迁移窗口）。
	 */
	PayNotifyAck aliPayNotify(String tenantId, String terminalType, HttpServletRequest request);

	/**
	 * 旧版微信退款通知入口（仅用于已发出的存量支付迁移窗口）。
	 */
	PayNotifyAck wxPayRefundNotify(String tenantId, String terminalType, String notifyData);

	/**
	 * 带身份版本的微信支付回调：路径版本只用于定位材料，仍需验签并核对订单冻结身份。
	 */
	PayNotifyAck wxPayNotifyV2(String tenantId, String revisionId, String notifyData);

	PayNotifyAck aliPayNotifyV2(String tenantId, String revisionId, HttpServletRequest request);

	PayNotifyAck wxPayRefundNotifyV2(String tenantId, String revisionId, String notifyData);

	/**
	 * 主动向渠道查询支付结果并落账（支付回调未达时由业务侧触发的权威核对）。
	 *
	 * @param tenantId 租户ID
	 * @param outTradeNo 商户订单号
	 * @return 核对结果；渠道未确认、无查单能力或查单失败时 paid=false
	 */
	PaySettlementResult queryAndSettlePayOrder(String tenantId, String outTradeNo);

	/**
	 * 取消业务订单前查单/关单；只有明确未支付且已关闭或从未发起才允许取消。
	 */
	PayCloseResult closeAndCheckPayOrder(String outTradeNo);

}
