
package com.aryn.cloud.common.core.desensitization;

import com.aryn.cloud.common.core.config.AbstractDesensitization;

/**
 * 手机号脱敏
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
public class MobilePhoneDesensitization extends AbstractDesensitization {

	@Override
	public String serialize(String value) {
		return value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
	}

}
