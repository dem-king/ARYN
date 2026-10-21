
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
	 * 管理端（TOB）短信验证码登录URL。
	 *
	 * <p>注意与 {@link #PHONE_SMS_TOKEN} 的区别： {@code /token/**} 在免登白名单内，
	 * 登录端点必须自己校验凭证；SmsValidateCodeFilter 依赖这个常量把管理端短信登录
	 * 纳入验证码校验，漏掉它会变成「知道管理员手机号即可签发 TOB token」。</p>
	 */
	String TOB_SMS_TOKEN = "/token/sms/login";

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
