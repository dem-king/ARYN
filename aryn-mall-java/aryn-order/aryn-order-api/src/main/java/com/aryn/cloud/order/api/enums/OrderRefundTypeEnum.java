
package com.aryn.cloud.order.api.enums;

public enum OrderRefundTypeEnum {

	ONLY_REFUND("1", "仅退款"), RETURN_AND_REFUND("2", "退货退款");

	private final String code;

	private final String description;

	OrderRefundTypeEnum(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public static OrderRefundTypeEnum fromCode(String code) {
		for (OrderRefundTypeEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的售后类型状态码: " + code);
	}

}
