package com.aryn.cloud.order.api.remote;

/**
 * 订单退款 Dubbo 远程服务接口
 *
 * <p>供营销等服务在系统侧需要自动退款时调用（当前场景：拼团成团失败，
 * 对已付款成员整单自动退款）。退款成功后走既有退款回调链路。
 */
public interface RemoteOrderRefundService {

	/**
	 * 整单自动退款（系统侧发起，跳过售后审核）
	 *
	 * <p>仅处理已支付、未取消的订单；按订单项逐项创建退款单并发起支付退款，
	 * 按订单项幂等（已有退款单的项跳过）。部分项已在售后流程中的项跳过。
	 *
	 * @param orderId 订单ID
	 * @param reason  退款原因（写入退款单）
	 * @return true=已发起至少一项退款；false=无需退款（未支付/已取消/均已退款）
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException 订单状态不支持退款
	 */
	boolean refundWholeOrder(String orderId, String reason);
}
