
package com.aryn.cloud.pay.api.enums;

import java.util.List;

/**
 * 支付材料用途。用途决定加密 AAD 与启用校验，不能跨渠道复用。
 */
public enum PayConfigMaterialPurposeEnum {

	WX_API_V3_KEY("WX_API_V3_KEY", PayChannelEnum.WECHAT, true),
	WX_MERCHANT_PRIVATE_KEY("WX_MERCHANT_PRIVATE_KEY", PayChannelEnum.WECHAT, true),
	WX_MERCHANT_CERT("WX_MERCHANT_CERT", PayChannelEnum.WECHAT, false),
	WX_PUBLIC_KEY("WX_PUBLIC_KEY", PayChannelEnum.WECHAT, false),

	ALI_APP_PRIVATE_KEY("ALI_APP_PRIVATE_KEY", PayChannelEnum.ALIPAY, true),
	ALI_APP_CERT("ALI_APP_CERT", PayChannelEnum.ALIPAY, false),
	ALI_PUBLIC_CERT("ALI_PUBLIC_CERT", PayChannelEnum.ALIPAY, false),
	ALI_ROOT_CERT("ALI_ROOT_CERT", PayChannelEnum.ALIPAY, false);

	private final String code;

	private final PayChannelEnum channel;

	private final boolean sensitive;

	PayConfigMaterialPurposeEnum(String code, PayChannelEnum channel, boolean sensitive) {
		this.code = code;
		this.channel = channel;
		this.sensitive = sensitive;
	}

	public String getCode() {
		return code;
	}

	public PayChannelEnum getChannel() {
		return channel;
	}

	/**
	 * 私钥与 APIv3 密钥属于不可回显内容。
	 */
	public boolean isSensitive() {
		return sensitive;
	}

	public static PayConfigMaterialPurposeEnum fromCode(String code) {
		for (PayConfigMaterialPurposeEnum purpose : values()) {
			if (purpose.code.equals(code)) {
				return purpose;
			}
		}
		return null;
	}

	/**
	 * 启用渠道必须齐备的材料。微信固定 APIv3 公钥模式，支付宝固定证书模式。
	 */
	public static List<PayConfigMaterialPurposeEnum> requiredFor(PayChannelEnum channel) {
		if (channel == PayChannelEnum.WECHAT) {
			return List.of(WX_API_V3_KEY, WX_MERCHANT_PRIVATE_KEY, WX_MERCHANT_CERT, WX_PUBLIC_KEY);
		}
		if (channel == PayChannelEnum.ALIPAY) {
			return List.of(ALI_APP_PRIVATE_KEY, ALI_APP_CERT, ALI_PUBLIC_CERT, ALI_ROOT_CERT);
		}
		return List.of();
	}

}
