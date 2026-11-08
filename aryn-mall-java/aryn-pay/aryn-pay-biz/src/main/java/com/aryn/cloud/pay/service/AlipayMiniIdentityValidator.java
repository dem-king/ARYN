package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 支付宝小程序买家身份校验。
 *
 * <p>两种身份模式严格隔离：user_id 只接受数字 UID，open_id 拒绝微信 openid 形态；
 * 调用方还必须通过 user-api 校验该身份确实属于当前租户/应用/商城用户。
 */
public final class AlipayMiniIdentityValidator {

	private static final Pattern USER_ID = Pattern.compile("^[0-9]{10,32}$");

	private static final Pattern OPEN_ID = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

	private static final Pattern WECHAT_OPEN_ID = Pattern.compile("^o[A-Za-z0-9_-]{27}$");

	private static final Pattern WECHAT_APP_ID = Pattern.compile("^wx[0-9a-fA-F]{16}$");

	private AlipayMiniIdentityValidator() {
	}

	/**
	 * 从 OAuth 响应中严格按配置模式取值，绝不回退到另一种身份。
	 */
	public static String select(MiniAppIdentityModeEnum mode, String userId, String openId) {
		if (mode == null) {
			throw new ArynBusinessException(403, "支付宝小程序身份模式未配置");
		}
		String identity = mode == MiniAppIdentityModeEnum.USER_ID ? userId : openId;
		return require(mode, identity);
	}

	public static String require(MiniAppIdentityModeEnum mode, String identity) {
		if (mode == null) {
			throw new ArynBusinessException(403, "支付宝小程序身份模式未配置");
		}
		if (!StringUtils.hasText(identity)) {
			throw new ArynBusinessException(403, "支付宝小程序买家身份缺失");
		}
		String value = identity.trim();
		if (WECHAT_APP_ID.matcher(value).matches()) {
			throw new ArynBusinessException(403, "支付宝买家身份不能使用微信 AppID");
		}
		if (mode == MiniAppIdentityModeEnum.USER_ID) {
			if (!USER_ID.matcher(value).matches()) {
				throw new ArynBusinessException(403, "支付宝买家 user_id 格式不合法");
			}
			return value;
		}
		if (WECHAT_OPEN_ID.matcher(value).matches() || !OPEN_ID.matcher(value).matches()) {
			throw new ArynBusinessException(403, "支付宝买家 open_id 格式不合法");
		}
		return value;
	}

}
