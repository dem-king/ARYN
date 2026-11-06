package com.aryn.cloud.promotion.api.enums;

/**
 * 销售员状态
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum SalesStaffStatusEnum {

	/** 在职 */
	STATUS_0("0", "在职"),
	/** 离职 */
	STATUS_1("1", "离职");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	SalesStaffStatusEnum(String code, String desc) {
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
		for (SalesStaffStatusEnum e : values()) {
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
