
package com.aryn.cloud.order.api.enums;

public enum OrderArrivalStatusEnum {

	NOT_REFUNDED("0", "未退款"), REFUNDING("1", "退款中"), REFUND_SUCCESS("2", "退款成功"), REFUND_FAIL("3", "退款失败");

	private String code;

	private String desc;

	OrderArrivalStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 自己定义一个静态方法,通过code返回枚举描述
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param code
	 * @return: java.lang.String
	 */
	public static String getValue(String code) {

		for (OrderArrivalStatusEnum orderStatusEnum : values()) {
			if (orderStatusEnum.getCode().equals(code)) {
				return orderStatusEnum.getDesc();
			}
		}
		return null;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

}
