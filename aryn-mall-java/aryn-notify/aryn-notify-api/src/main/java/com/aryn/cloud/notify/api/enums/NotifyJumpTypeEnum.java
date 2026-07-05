
package com.aryn.cloud.notify.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息跳转类型枚举
 *
 * @author aryn
 * @since 2026/07/05
 */
@Getter
@AllArgsConstructor
public enum NotifyJumpTypeEnum {

	NONE(0, "不跳转"),
	ORDER_DETAIL(1, "订单详情"),
	GOODS_DETAIL(2, "商品详情"),
	ACTIVITY(3, "活动页"),
	CUSTOM(4, "自定义链接");

	private final Integer code;

	private final String desc;

}
