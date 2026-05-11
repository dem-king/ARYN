
package com.aryn.cloud.promotion.api.enums;

/**
 * 佣金流水类型：INCOME收入 EXPENSE支出
 *
 * @author 雨滴kian
 * @since 2025/4/8
 */
public enum CommissionFlowTypeEnum {

	/** 收入 */
	INCOME("INCOME", "收入"),
	/** 支出 */
	EXPENSE("EXPENSE", "支出");

	/** code */
	private String code;

	/** 描述 */
	private String desc;

	CommissionFlowTypeEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过code返回枚举描述
	 *
	 * @param code 类型码
	 * @return 枚举描述
	 */
	public static String getValue(String code) {
		for (CommissionFlowTypeEnum e : values()) {
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
