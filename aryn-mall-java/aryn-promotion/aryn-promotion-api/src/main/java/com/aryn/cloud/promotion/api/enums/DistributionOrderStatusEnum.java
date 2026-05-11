package com.aryn.cloud.promotion.api.enums;

/**
 * 分销订单状态：0待结算 1已结算 2已退款
 *
 * @author 雨滴kian
 * @since 2025/4/8
 */
public enum DistributionOrderStatusEnum {

	/** 待结算 */
	STATUS_0("0", "待结算"),
	/** 已结算 */
	STATUS_1("1", "已结算"),
	/** 已退款 */
	STATUS_2("2", "已退款");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	DistributionOrderStatusEnum(String code, String desc) {
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
		for (DistributionOrderStatusEnum e : values()) {
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
