package com.aryn.cloud.order.api.enums;

/**
 * 购买场景枚举
 *
 * <p>订单级场景：同一商品可能同时支持个人购买和船供采购，
 * 订单必须保存场景快照，与商品级 sale_scope 相互独立。
 *
 * @author aryn
 * @since 2026/9/11
 */
public enum PurchaseSceneEnum {

	/**
	 * 海员个人购买
	 */
	PERSONAL("1", "海员个人购买"),

	/**
	 * 船供采购
	 */
	SHIP_SUPPLY("2", "船供采购");

	private final String code;

	private final String desc;

	PurchaseSceneEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过 code 返回枚举描述，无效 code 返回 null，不回退到默认场景
	 */
	public static String getValue(String code) {
		for (PurchaseSceneEnum sceneEnum : values()) {
			if (sceneEnum.getCode().equals(code)) {
				return sceneEnum.getDesc();
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
