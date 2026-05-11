
package com.aryn.cloud.promotion.api.enums;

/**
 * 提现审核状态：0待审核 1已通过 2已拒绝
 *
 * @author 雨滴kian
 * @since 2025/4/8
 */
public enum DistributionWithdrawStatusEnum {

	/** 待审核 */
	STATUS_0("0", "待审核"),
	/** 已通过 */
	STATUS_1("1", "已通过"),
	/** 已拒绝 */
	STATUS_2("2", "已拒绝");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	DistributionWithdrawStatusEnum(String code, String desc) {
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
		for (DistributionWithdrawStatusEnum e : values()) {
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
