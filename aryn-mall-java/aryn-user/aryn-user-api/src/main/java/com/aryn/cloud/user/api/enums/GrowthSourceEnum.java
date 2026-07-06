package com.aryn.cloud.user.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 成长值来源枚举
 *
 * @author aryn
 */
@Getter
@AllArgsConstructor
public enum GrowthSourceEnum {

	/**
	 * 下单
	 */
	ORDER("order", 10),

	/**
	 * 签到
	 */
	SIGN_IN("sign_in", 2),

	/**
	 * 评价
	 */
	REVIEW("review", 5),

	/**
	 * 退款
	 */
	REFUND("refund", -10),

	/**
	 * 管理员手动调整
	 */
	ADMIN("admin", 0);

	private final String code;

	private final int defaultValue;

	/**
	 * 根据来源编码获取默认成长值
	 * @param code 来源编码
	 * @return 默认成长值
	 */
	public static int getDefaultValue(String code) {
		for (GrowthSourceEnum source : values()) {
			if (source.getCode().equals(code)) {
				return source.getDefaultValue();
			}
		}
		return 0;
	}

}