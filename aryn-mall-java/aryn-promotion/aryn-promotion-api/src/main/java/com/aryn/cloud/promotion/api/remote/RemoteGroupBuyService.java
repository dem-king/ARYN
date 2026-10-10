package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;

/**
 * 拼团 Dubbo 远程服务接口
 *
 * <p>供订单服务在拼团下单链路中调用：开团/参团占坑成功后，
 * 下单校验取拼团价、订单落库后绑定成员、订单取消释放占坑。
 */
public interface RemoteGroupBuyService {

	/**
	 * 拼团下单校验并取拼团价上下文
	 *
	 * <p>校验拼团记录有效（进行中、未超时）、活动有效、当前用户为该团未付款且未绑定订单的
	 * 成员；校验失败抛业务异常。返回活动 SKU 与拼团价，由订单服务按 SKU 匹配落到明细成交基价。
	 *
	 * @param recordId 拼团记录ID（开团/参团返回）
	 * @param userId   下单用户ID
	 * @return 拼团价上下文（含活动 SKU 与拼团价）
	 */
	GroupBuyOrderContextVO getOrderContext(String recordId, String userId);

	/**
	 * 订单创建成功后把订单号绑定到拼团成员（成团判定依赖该关联）
	 *
	 * <p>乐观抢占：仅当成员仍为待付款且未绑定订单时生效；并发重复下单时返回 false，
	 * 调用方应阻断下单。
	 *
	 * @param recordId 拼团记录ID
	 * @param userId   下单用户ID
	 * @param orderId  订单ID
	 * @return true=绑定成功
	 */
	boolean bindOrder(String recordId, String userId, String orderId);

	/**
	 * 订单取消/超时取消时释放拼团成员占坑
	 *
	 * <p>按订单号定位成员，清空订单关联并回到待付款，允许该成员重新下单；
	 * 商品库存已由订单流在取消时回滚，此处只释放占坑关联。方法幂等。
	 *
	 * @param orderId 订单ID
	 * @return true=已释放（无关联记录时也返回 true，幂等）
	 */
	boolean releaseOrder(String orderId);
}
