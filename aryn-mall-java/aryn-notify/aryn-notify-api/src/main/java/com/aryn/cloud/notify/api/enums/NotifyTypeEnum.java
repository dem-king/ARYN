
package com.aryn.cloud.notify.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息类型枚举
 *
 * @author aryn
 * @since 2026/07/05
 */
@Getter
@AllArgsConstructor
public enum NotifyTypeEnum {

	ORDER(1, "订单消息"),
	PAY(2, "支付消息"),
	LOGISTICS(3, "物流消息"),
	MARKETING(4, "营销消息"),
	SYSTEM(5, "系统消息"),
	SOCIAL(6, "社交消息");

	private final Integer code;

	private final String desc;

	public static NotifyTypeEnum getByCode(Integer code) {
		if (code == null) {
			return null;
		}
		for (NotifyTypeEnum type : values()) {
			if (type.getCode().equals(code)) {
				return type;
			}
		}
		return null;
	}

}
