package com.aryn.cloud.promotion.api.enums;

/**
 * 销售发放台账状态
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum SalesPayoutStatusEnum {

	/** 待确认 */
	STATUS_0("0", "待确认"),
	/** 已发放 */
	STATUS_1("1", "已发放");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	SalesPayoutStatusEnum(String code, String desc) {
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
		for (SalesPayoutStatusEnum e : values()) {
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
