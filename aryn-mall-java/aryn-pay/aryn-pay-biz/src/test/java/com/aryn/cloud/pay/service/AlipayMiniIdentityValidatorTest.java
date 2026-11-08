package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlipayMiniIdentityValidatorTest {

	@Test
	void userIdModeSelectsOnlyUserId() {
		assertThat(AlipayMiniIdentityValidator.select(MiniAppIdentityModeEnum.USER_ID, "2088123456789012",
				"074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5")).isEqualTo("2088123456789012");
		assertThatThrownBy(() -> AlipayMiniIdentityValidator.select(MiniAppIdentityModeEnum.USER_ID, null,
				"074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序买家身份缺失");
	}

	@Test
	void openIdModeSelectsOnlyOpenIdAndRejectsWechatShape() {
		assertThat(AlipayMiniIdentityValidator.select(MiniAppIdentityModeEnum.OPEN_ID, "2088123456789012",
				"074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5"))
			.isEqualTo("074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5");
		assertThatThrownBy(() -> AlipayMiniIdentityValidator.require(MiniAppIdentityModeEnum.OPEN_ID,
				"ozexS3bXeBjIkrfIZoe07SWXBY4I"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝买家 open_id 格式不合法");
	}

	@Test
	void neverAcceptsWechatAppIdAsBuyerIdentity() {
		assertThatThrownBy(() -> AlipayMiniIdentityValidator.require(MiniAppIdentityModeEnum.OPEN_ID,
				"wx0a8242ea59f3e6b4"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝买家身份不能使用微信 AppID");
		assertThatThrownBy(() -> AlipayMiniIdentityValidator.require(MiniAppIdentityModeEnum.USER_ID,
				"wx0a8242ea59f3e6b4"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝买家身份不能使用微信 AppID");
	}

	@Test
	void missingModeFailsClosed() {
		assertThatThrownBy(() -> AlipayMiniIdentityValidator.select(null, "2088123456789012", null))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序身份模式未配置");
	}

}
