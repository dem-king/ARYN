package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 小程序支付平台一致性约束。
 *
 * <p>平台字段来自服务端读取的请求头，仅用于路由/体验约束；真正的资金安全仍由
 * 渠道验签、商户/租户/金额校验与买家身份归属校验保证。
 */
public final class PayClientPlatform {

	private static final String WX_MINI_ALIAS = "WX_MINI";

	private PayClientPlatform() {
	}

	public static String requireWxMini(String platform) {
		String normalized = normalize(platform);
		if (!StringUtils.hasText(normalized)) {
			throw new ArynBusinessException(403, "缺少客户端平台，不能发起小程序支付");
		}
		if (!OpenPlatformTypeEnum.WX_MA.getCode().equals(normalized)) {
			throw new ArynBusinessException(403, "支付平台与微信小程序支付不匹配");
		}
		return normalized;
	}

	public static String requireAliMini(String platform) {
		String normalized = normalize(platform);
		if (!StringUtils.hasText(normalized)) {
			throw new ArynBusinessException(403, "缺少客户端平台，不能发起小程序支付");
		}
		if (!OpenPlatformTypeEnum.ALI_MINI.getCode().equals(normalized)) {
			throw new ArynBusinessException(403, "支付平台与支付宝小程序支付不匹配");
		}
		return normalized;
	}

	public static boolean isWxMini(String platform) {
		return OpenPlatformTypeEnum.WX_MA.getCode().equals(normalize(platform));
	}

	public static boolean isAliMini(String platform) {
		return OpenPlatformTypeEnum.ALI_MINI.getCode().equals(normalize(platform));
	}

	public static String normalize(String platform) {
		if (!StringUtils.hasText(platform)) {
			return "";
		}
		String value = platform.trim().toUpperCase(Locale.ROOT);
		return WX_MINI_ALIAS.equals(value) ? OpenPlatformTypeEnum.WX_MA.getCode() : value;
	}

}
