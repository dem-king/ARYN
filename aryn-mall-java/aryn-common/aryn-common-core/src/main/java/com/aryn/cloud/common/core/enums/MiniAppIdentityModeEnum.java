package com.aryn.cloud.common.core.enums;

import lombok.Getter;

import java.util.Locale;

/**
 * 支付宝小程序买家身份模式。
 *
 * <p>支付宝同时提供 user_id 与 open_id 两种买家标识，必须由使用方显式配置；
 * 两种模式不可互相回退或猜测，缺失/未知一律拒绝。
 */
@Getter
public enum MiniAppIdentityModeEnum {

	USER_ID("user_id", "支付宝 user_id"),
	OPEN_ID("open_id", "支付宝 open_id");

	private final String code;

	private final String desc;

	MiniAppIdentityModeEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public static MiniAppIdentityModeEnum fromCode(String code) {
		if (code == null) {
			return null;
		}
		String normalized = code.trim().toLowerCase(Locale.ROOT);
		for (MiniAppIdentityModeEnum mode : values()) {
			if (mode.code.equals(normalized)) {
				return mode;
			}
		}
		return null;
	}

}
