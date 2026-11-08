
package com.aryn.cloud.pay.api.enums;

import lombok.Getter;

/**
 * 支付场景契约。name 是历史沿用的交易类型编码，同时决定 Handler Bean 名称。
 *
 * <p>implemented=false 只表示协议契约已预留、尚未交付，能力接口不得下发；
 * 不能仅凭枚举存在就把渠道展示成可用。
 */
@Getter
public enum PayTradeTypeEnum {

	/**
	 * 微信小程序 / 公众号内 JSAPI 支付
	 */
	WX_JSAPI_PAY("WX_JSAPI_PAY", "微信JSAPI支付", PayChannelEnum.WECHAT, PayTerminalTypeEnum.MINI_PROGRAM,
			PayInvokeTypeEnum.WX_MINI, true),

	/**
	 * 微信 APP 支付
	 */
	WX_APP_PAY("WX_APP_PAY", "微信APP支付", PayChannelEnum.WECHAT, PayTerminalTypeEnum.APP, PayInvokeTypeEnum.WX_APP,
			true),

	/**
	 * 微信外部浏览器 H5 支付（P1 交付前不对外可用）
	 */
	WX_H5_PAY("WX_H5_PAY", "微信H5支付", PayChannelEnum.WECHAT, PayTerminalTypeEnum.H5, PayInvokeTypeEnum.REDIRECT,
			true),

	/**
	 * 支付宝 APP 支付
	 */
	ALI_APP_PAY("ALI_APP_PAY", "支付宝APP支付", PayChannelEnum.ALIPAY, PayTerminalTypeEnum.APP,
			PayInvokeTypeEnum.ALI_APP, true),

	/**
	 * 支付宝 H5 支付
	 */
	ALI_H5_PAY("ALI_H5_PAY", "支付宝H5支付", PayChannelEnum.ALIPAY, PayTerminalTypeEnum.H5, PayInvokeTypeEnum.REDIRECT,
			true),

	/**
	 * 支付宝小程序支付：需显式配置买家身份模式并完成三方账号绑定校验
	 */
	ALI_JSAPI_PAY("ALI_JSAPI_PAY", "支付宝小程序支付", PayChannelEnum.ALIPAY, PayTerminalTypeEnum.MINI_PROGRAM,
			PayInvokeTypeEnum.ALI_MINI, true),

	/**
	 * 0元支付
	 */
	FREE_PAY("FREE_PAY", "0元支付", PayChannelEnum.FREE, null, PayInvokeTypeEnum.NONE, true);

	/**
	 * 交易类型编码（历史字段名 name）
	 */
	private final String name;

	private final String description;

	private final PayChannelEnum channel;

	private final PayTerminalTypeEnum terminal;

	private final PayInvokeTypeEnum invokeType;

	/**
	 * 是否已经交付可对外下发的协议实现。
	 */
	private final boolean implemented;

	PayTradeTypeEnum(String name, String description, PayChannelEnum channel, PayTerminalTypeEnum terminal,
			PayInvokeTypeEnum invokeType, boolean implemented) {
		this.name = name;
		this.description = description;
		this.channel = channel;
		this.terminal = terminal;
		this.invokeType = invokeType;
		this.implemented = implemented;
	}

	public static PayTradeTypeEnum fromCode(String code) {
		for (PayTradeTypeEnum type : values()) {
			if (type.name.equals(code)) {
				return type;
			}
		}
		return null;
	}

}
