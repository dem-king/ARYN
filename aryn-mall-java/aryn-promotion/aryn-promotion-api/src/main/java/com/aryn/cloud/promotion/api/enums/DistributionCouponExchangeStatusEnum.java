package com.aryn.cloud.promotion.api.enums;

/**
 * 券兑换状态
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public enum DistributionCouponExchangeStatusEnum {

	/** 发放中 */
	STATUS_0("0", "发放中"),
	/** 已发放 */
	STATUS_1("1", "已发放"),
	/** 发放失败 */
	STATUS_2("2", "发放失败");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	DistributionCouponExchangeStatusEnum(String code, String desc) {
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
		for (DistributionCouponExchangeStatusEnum e : values()) {
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
