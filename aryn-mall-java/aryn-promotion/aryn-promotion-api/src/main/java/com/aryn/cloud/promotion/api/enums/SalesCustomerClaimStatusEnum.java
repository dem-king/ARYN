package com.aryn.cloud.promotion.api.enums;

/**
 * 客户待转交认领状态
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum SalesCustomerClaimStatusEnum {

	/** 待首单 */
	STATUS_0("0", "待首单"),
	/** 已转交 */
	STATUS_1("1", "已转交"),
	/** 已失效 */
	STATUS_2("2", "已失效");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	SalesCustomerClaimStatusEnum(String code, String desc) {
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
		for (SalesCustomerClaimStatusEnum e : values()) {
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
