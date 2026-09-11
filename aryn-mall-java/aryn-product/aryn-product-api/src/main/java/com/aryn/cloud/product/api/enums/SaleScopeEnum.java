package com.aryn.cloud.product.api.enums;

/**
 * 商品销售范围枚举
 *
 * <p>商品级销售范围：显式表达商品可用于哪些购买场景，
 * 避免用单一 is_ship_supply 布尔值无法表达“个人和船供均可”。
 *
 * @author aryn
 * @since 2026/9/11
 */
public enum SaleScopeEnum {

	/**
	 * 仅个人购买
	 */
	PERSONAL_ONLY("1", "仅个人购买"),

	/**
	 * 仅船供采购
	 */
	SHIP_SUPPLY_ONLY("2", "仅船供采购"),

	/**
	 * 个人购买和船供采购
	 */
	BOTH("3", "个人购买和船供采购");

	private final String code;

	private final String desc;

	SaleScopeEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过 code 返回枚举描述，无效 code 返回 null，不回退到默认范围
	 */
	public static String getValue(String code) {
		if (code == null) {
			return null;
		}
		for (SaleScopeEnum scopeEnum : values()) {
			if (scopeEnum.getCode().equals(code)) {
				return scopeEnum.getDesc();
			}
		}
		return null;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

}
