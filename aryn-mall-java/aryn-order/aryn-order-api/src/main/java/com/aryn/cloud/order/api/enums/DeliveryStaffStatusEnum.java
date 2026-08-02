
package com.aryn.cloud.order.api.enums;

/**
 * 配送员状态枚举
 *
 * @author aryn
 * @since 2025/7/31
 */
public enum DeliveryStaffStatusEnum {

	ONLINE("1", "在线"), BUSY("2", "忙碌"), OFFLINE("3", "离线");

	private final String code;

	private final String desc;

	DeliveryStaffStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

	public static DeliveryStaffStatusEnum fromCode(String code) {
		for (DeliveryStaffStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的配送员状态码: " + code);
	}

	public static String getValue(String code) {
		for (DeliveryStaffStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status.desc;
			}
		}
		return null;
	}

}