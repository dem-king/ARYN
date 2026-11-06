package com.aryn.cloud.promotion.api.enums;

/**
 * 销售业绩单状态
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum SalesOrderStatusEnum {

	/** 待确认 */
	STATUS_0("0", "待确认"),
	/** 可发放 */
	STATUS_1("1", "可发放"),
	/** 已发放 */
	STATUS_2("2", "已发放"),
	/** 已全额退款 */
	STATUS_3("3", "已全额退款");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	SalesOrderStatusEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过code返回枚举描述
	 *
	 * @param code 状态码
	 * @return 枚举描述
	 */
	public static String getValue(String code) {
		for (SalesOrderStatusEnum e : values()) {
			if (e.getCode().equals(code)) {
				return e.getDesc();
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
