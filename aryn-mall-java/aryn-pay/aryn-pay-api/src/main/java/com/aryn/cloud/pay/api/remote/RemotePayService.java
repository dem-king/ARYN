
package com.aryn.cloud.pay.api.remote;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.vo.PayCapabilityVO;

import java.util.List;

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

	/**
	 * 取消业务订单前的支付门禁：先查单/关单，再决定能否释放库存与优惠。
	 *
	 * <p>只有渠道明确未支付且已关闭，或确认从未发起支付，才返回可取消；
	 * 网络失败、响应不明、暂未查到时返回 UNKNOWN，调用方不得释放资源。
	 *
	 * @param outTradeNo 商户订单号
	 * @return 关单核对结果
	 */
	PayCloseResult closeAndCheckPayOrder(String outTradeNo);

	/**
	 * 查询当前租户指定终端真正可用的支付场景。
	 *
	 * <p>只返回已实现、已启用且本地材料合格的渠道；不得返回商户密钥、
	 * 证书路径或“已签约”一类无法由本地证明的结论。
	 *
	 * @param terminalType 支付端类型；为空时返回全部终端
	 * @return 支付能力清单
	 */
	List<PayCapabilityVO> queryPayCapabilities(String terminalType);

}
