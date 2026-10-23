package com.aryn.cloud.order.api.enums;

/**
 * 配送方式枚举
 *
 * <p>配送方式统一为 1 普通快递 / 2 上门自提 / 3 公司配送（岸上）/ 4 公司配送（到船）。
 * 3 与 4 均由公司自有运力送达：3 送到收货地址并受配送范围限制，
 * 4 由公司司机按靠港计划送到港口或船舶，不产生快递公司单号和第三方物流轨迹。
 *
 * @author aryn
 * @since 2026/9/11
 */
public enum DeliveryWayEnum {

	/**
	 * 普通快递
	 */
	EXPRESS("1", "普通快递"),

	/**
	 * 上门自提
	 */
	SELF_PICKUP("2", "上门自提"),

	/**
	 * 公司配送（岸上）：公司配送员送到收货地址
	 */
	MALL_DELIVERY("3", "公司配送（岸上）"),

	/**
	 * 公司配送（到船）：公司司机按靠港计划送到港口或船舶
	 */
	INTERNAL_PORT("4", "公司配送（到船）");

	private final String code;

	private final String desc;

	DeliveryWayEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	/**
	 * 通过 code 返回枚举描述，无效 code 返回 null，不回退成普通快递
	 */
	public static String getValue(String code) {
		if (code == null) {
			return null;
		}
		for (DeliveryWayEnum wayEnum : values()) {
			if (wayEnum.getCode().equals(code)) {
				return wayEnum.getDesc();
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
