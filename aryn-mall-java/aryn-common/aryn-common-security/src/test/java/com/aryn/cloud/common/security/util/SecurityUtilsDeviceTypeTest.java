package com.aryn.cloud.common.security.util;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsDeviceTypeTest {

	@Test
	void shouldAcceptMatchingDeviceType() {
		ArynUser user = new ArynUser();
		user.setDeviceType(DeviceTypeEnum.TOB);

		assertThat(SecurityUtils.requireDevice(user, DeviceTypeEnum.TOB)).isSameAs(user);
	}

	@Test
	void shouldRejectMismatchedDeviceType() {
		ArynUser user = new ArynUser();
		user.setDeviceType(DeviceTypeEnum.TOC);

		assertThatThrownBy(() -> SecurityUtils.requireDevice(user, DeviceTypeEnum.TOB))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前登录端无权访问该资源");
	}

	@Test
	void shouldRejectMissingUserOrDeviceType() {
		assertThatThrownBy(() -> SecurityUtils.requireDevice(null, DeviceTypeEnum.TOB))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前会话未登录");

		ArynUser user = new ArynUser();
		assertThatThrownBy(() -> SecurityUtils.requireDevice(user, DeviceTypeEnum.TOB))
			.isInstanceOf(ArynBusinessException.class)
			.hasFieldOrPropertyWithValue("msg", "当前登录端无权访问该资源");
	}

	@Test
	void shouldParseLegacySaTokenDeviceType() {
		assertThat(SecurityUtils.parseDeviceType("TOB")).isEqualTo(DeviceTypeEnum.TOB);
		assertThat(SecurityUtils.parseDeviceType("toc")).isEqualTo(DeviceTypeEnum.TOC);
		assertThat(SecurityUtils.parseDeviceType(null)).isNull();
		assertThat(SecurityUtils.parseDeviceType("UNKNOWN")).isNull();
	}
}
