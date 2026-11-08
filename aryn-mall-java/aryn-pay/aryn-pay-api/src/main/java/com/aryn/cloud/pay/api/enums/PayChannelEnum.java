
package com.aryn.cloud.pay.api.enums;

/**
 * 支付渠道。
 */
public enum PayChannelEnum {

	FREE("0", "零元支付"),
	WECHAT("1", "微信支付"),
	ALIPAY("2", "支付宝");

	private final String code;

	private final String desc;

	PayChannelEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

	public String getCode() {
		return code;
	}

	public String getDesc() {
		return desc;
	}

	public static PayChannelEnum fromCode(String code) {
		for (PayChannelEnum channel : values()) {
			if (channel.code.equals(code)) {
				return channel;
			}
		}
		return null;
	}

}
