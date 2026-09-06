package com.aryn.cloud.common.core.constant;

/**
 * 配送身份认证常量（认证服务与订单域守卫共享）
 *
 * @author aryn
 * @since 2026/9/6
 */
public interface DeliveryAuthConstants {

	/** token 会话标记 key：身份来源于商城绑定换取 */
	String MALL_BINDING_SOURCE = "delivery_mall_binding_source";

	/** token 会话标记值：商城绑定换取 */
	String MALL_BINDING_SOURCE_VALUE = "mall_exchange";

}
