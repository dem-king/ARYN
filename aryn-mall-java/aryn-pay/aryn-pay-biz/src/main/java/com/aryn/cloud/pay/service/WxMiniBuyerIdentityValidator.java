package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 微信小程序买家身份格式防线：拒绝把支付宝 user_id/open_id 形态的标识
 * 写入微信 JSAPI 的 payer.openid。最终支付成败仍由微信渠道校验。
 */
public final class WxMiniBuyerIdentityValidator {

	private static final Pattern ALIPAY_USER_ID = Pattern.compile("^[0-9]{16}$");

	private static final Pattern ALIPAY_OPEN_ID = Pattern.compile("^0[A-Za-z0-9_-]{31,127}$");

	private WxMiniBuyerIdentityValidator() {
	}

	public static String require(String buyerIdentity) {
		if (!StringUtils.hasText(buyerIdentity)) {
			throw new ArynBusinessException(403, "微信小程序买家身份缺失");
		}
		String value = buyerIdentity.trim();
		if (ALIPAY_USER_ID.matcher(value).matches() || ALIPAY_OPEN_ID.matcher(value).matches()) {
			throw new ArynBusinessException(403, "微信小程序买家身份不能使用支付宝身份");
		}
		if (value.length() > 128) {
			throw new ArynBusinessException(403, "微信小程序买家身份格式不合法");
		}
		return value;
	}

}
