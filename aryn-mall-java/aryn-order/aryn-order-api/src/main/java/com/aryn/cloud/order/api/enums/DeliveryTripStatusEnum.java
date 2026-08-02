
package com.aryn.cloud.order.api.enums;

/**
 * 出车单状态枚举
 *
 * @author aryn
 * @since 2025/7/31
 */
public enum DeliveryTripStatusEnum {

	WAITING_LOAD("1", "待配货"), LOADING("2", "配货中"), DELIVERING("3", "配送中"), COMPLETED("4", "已完成");

	private final String code;

	private final String desc;

	DeliveryTripStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

	public static DeliveryTripStatusEnum fromCode(String code) {
		for (DeliveryTripStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的出车单状态码: " + code);
	}

	public static String getValue(String code) {
		for (DeliveryTripStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status.desc;
			}
		}
		return null;
	}

}