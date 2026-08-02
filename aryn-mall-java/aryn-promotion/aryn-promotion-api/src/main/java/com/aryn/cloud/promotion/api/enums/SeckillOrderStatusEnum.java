package com.aryn.cloud.promotion.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 秒杀订单状态:0未支付(预扣) 1已支付 2已取消 3已超时
 */
@Getter
@AllArgsConstructor
public enum SeckillOrderStatusEnum {

	UNPAID(0, "未支付"),
	PAID(1, "已支付"),
	CANCELED(2, "已取消"),
	EXPIRED(3, "已超时");

	private final Integer code;
	private final String msg;

	public static boolean isValid(Integer code) {
		if (code == null) {
			return false;
		}
		for (SeckillOrderStatusEnum e : values()) {
			if (e.code.equals(code)) {
				return true;
			}
		}
		return false;
	}
}