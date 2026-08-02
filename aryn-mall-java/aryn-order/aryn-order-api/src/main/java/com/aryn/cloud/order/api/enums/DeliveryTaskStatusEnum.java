
package com.aryn.cloud.order.api.enums;

/**
 * 配送任务状态枚举
 *
 * @author aryn
 * @since 2025/7/31
 */
public enum DeliveryTaskStatusEnum {

	WAITING_ASSIGN("1", "待派单"), WAITING_PICK("2", "待取货"), PICKING("3", "配货中"), WAITING_ARRIVE("4", "待送达"),
	ARRIVED("5", "已送达"), SIGNED("6", "已签收"), CANCELED("7", "已取消"), EXCEPTION("8", "异常"),
	RETURN_PENDING("9", "待退回");

	private final String code;

	private final String desc;

	DeliveryTaskStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

	public static DeliveryTaskStatusEnum fromCode(String code) {
		for (DeliveryTaskStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的配送任务状态码: " + code);
	}

	public static String getValue(String code) {
		for (DeliveryTaskStatusEnum status : values()) {
			if (status.code.equals(code)) {
				return status.desc;
			}
		}
		return null;
	}

}