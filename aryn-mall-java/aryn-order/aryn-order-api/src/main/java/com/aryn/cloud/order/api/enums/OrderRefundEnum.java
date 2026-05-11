
package com.aryn.cloud.order.api.enums;

/**
 * 商城退款单状态枚举
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
public enum OrderRefundEnum {

	PENDING_REVIEW("1", "待审核"), REFUNDING("5", "退款中"), REFUND_COMPLETED("6", "退款完成"), REFUND_FAILED("8", "退款失败"),
	REVIEW_REJECTED("9", "审核拒绝");

	private final String code;

	private final String description;

	OrderRefundEnum(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public String getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public static OrderRefundEnum fromCode(String code) {
		for (OrderRefundEnum status : values()) {
			if (status.code.equals(code)) {
				return status;
			}
		}
		throw new IllegalArgumentException("未知的售后状态码: " + code);
	}

}
