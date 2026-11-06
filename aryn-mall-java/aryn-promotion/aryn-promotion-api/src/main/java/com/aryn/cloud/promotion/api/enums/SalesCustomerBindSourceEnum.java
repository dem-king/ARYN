package com.aryn.cloud.promotion.api.enums;

/**
 * 客户归属绑定来源
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum SalesCustomerBindSourceEnum {

	/** 分享链接 */
	SHARE("SHARE", "分享链接"),
	/** 推广码 */
	QRCODE("QRCODE", "推广码"),
	/** 后台指派 */
	ADMIN("ADMIN", "后台指派"),
	/** 离职交接 */
	TRANSFER("TRANSFER", "离职交接"),
	/** 首单转交 */
	CLAIM("CLAIM", "首单转交");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	SalesCustomerBindSourceEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过code返回枚举描述
	 *
	 * @param code 来源码
	 * @return 枚举描述
	 */
	public static String getValue(String code) {
		for (SalesCustomerBindSourceEnum e : values()) {
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
