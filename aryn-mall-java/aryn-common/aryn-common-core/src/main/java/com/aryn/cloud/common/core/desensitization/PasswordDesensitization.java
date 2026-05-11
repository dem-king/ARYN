
package com.aryn.cloud.common.core.desensitization;

import com.aryn.cloud.common.core.config.AbstractDesensitization;

/**
 * 密码脱敏
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
public class PasswordDesensitization extends AbstractDesensitization {

	@Override
	public String serialize(String value) {
		return "******";
	}

}
