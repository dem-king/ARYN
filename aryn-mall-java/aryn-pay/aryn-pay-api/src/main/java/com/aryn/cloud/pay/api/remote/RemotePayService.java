
package com.aryn.cloud.pay.api.remote;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;

/**
 * @author 雨滴kian
 */
public interface RemotePayService {

	Object createOrder(CreateOrderReqDTO createOrderReqDTO);

	/**
	 * 主动向渠道核对指定商户订单号的支付结果，确认已支付则完成落账。
	 *
	 * <p>支付成功后渠道会回调，但回调经「渠道 → 回调接口 → MQ」异步投递，
	 * 可能延迟甚至丢失。业务侧在自家单据仍待支付时调用本方法，由支付域
	 * 直接向渠道查单取权威结论，避免用户因回调慢而长期看到「处理中」。
	 *
	 * <p>确认成功时与回调走同一条落账路径（幂等推进支付单 → 记录通知 →
	 * 广播 pay-notify-topic），下游业务无需区分「回调」与「查单」，
	 * 按既有支付成功监听照常入账。
	 *
	 * @param outTradeNo 商户订单号
	 * @return 核对结果；渠道未确认（未支付/已关闭）、无查单能力或查单失败时
	 *         返回 {@code paid=false}，调用方按既有状态展示即可
	 */
	PaySettlementResult queryAndSettlePayOrder(String outTradeNo);

}
