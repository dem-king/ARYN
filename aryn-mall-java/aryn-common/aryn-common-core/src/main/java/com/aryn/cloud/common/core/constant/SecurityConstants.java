
package com.aryn.cloud.common.core.constant;

/**
 * 安全相关常量
 *
 * @author 雨滴kian
 * @since 2022/2/17 15:39
 */
public interface SecurityConstants {

	/**
	 * 登录URL
	 */
	String OAUTH_TOKEN = "/token/login";

	/**
	 * 短信验证码登录URL
	 */
	String PHONE_SMS_TOKEN = "/token/phone/login";

	/**
	 * TOC短信验证码登录URL
	 */
	String TOC_PHONE_SMS_TOKEN = "/toc-token/sms/login";

	/**
	 * 注册
	 */
	String REGISTER = "/tenant/register";

	/**
	 * 来源
	 */
	String SOURCE = "source";

	/**
	 * 系统内部
	 */
	String SOURCE_IN = "source_in";

	/**
	 * 系统外部
	 */
	String SOURCE_OUTER = "source_outer";

}
