
package com.aryn.cloud.common.core.constant;

/**
 * 缓存常量
 *
 * @author 雨滴kian
 * @since 2022/2/18 17:00
 */
public interface CacheConstants {

	/** 验证码key */
	String DEFAULT_CODE_KEY = "random_code_";

	/** 短信验证码key */
	String SMS_CODE_KEY = "sms_code_";

	/** 短信验证码校验失败次数key（达到上限后验证码作废） */
	String SMS_CODE_ATTEMPT_KEY = "sms_code_attempt_";

	/** 短信发送冷却key（手机号维度，防连点，60s） */
	String SMS_SEND_COOLDOWN_KEY = "sms_send_cooldown_";

	/** 短信发送小时配额key（手机号维度，防单号轰炸） */
	String SMS_SEND_PHONE_HOUR_KEY = "sms_send_phone_hour_";

	/** 短信发送小时配额key（IP维度，防换号刷短信） */
	String SMS_SEND_IP_HOUR_KEY = "sms_send_ip_hour_";

	/** 短信发送小时配额key（实例/集群兜底维度，防 IP 头伪造绕过） */
	String SMS_SEND_GLOBAL_HOUR_KEY = "sms_send_global_hour";

	/**
	 * aj-captcha 二次校验缓存 key 前缀。
	 *
	 * <p>与 aj-captcha 1.4.0 `AbstractCaptchaService.REDIS_SECOND_CAPTCHA_KEY`（`RUNNING:CAPTCHA:second-%s`）
	 * 保持一致：`/code/check` 成功后把校验票据写入该 key（TTL 3 分钟），业务侧用
	 * 「前缀 + captchaVerification」做一次存在性校验并删除（一次性）。
	 * 该格式由 `CaptchaSecondVerificationKeyContractTest` 反射比对库常量守住，升级 aj-captcha 时必须同步。</p>
	 */
	String CAPTCHA_SECOND_VERIFICATION_KEY = "RUNNING:CAPTCHA:second-";

	/** 用户信息缓存 */
	String USER_CACHE = "user_cache";

	/** 系统在线用户 */
	String SYS_ONLINE_KEY = "satoken:login:online_user:";

	/** 菜单信息缓存 */
	String MENU_CACHE = "menu_cache";

	/** 字典缓存 */
	String DICT_CACHE = "dict_cache";

	/** 三方平台账号缓存 */
	String SOCIAL_ACCOUNT_CACHE = "social_account_cache";

	/** 首页页面设计缓存 */
	String HOME_PAGE_DESIGN_CACHE = "home_page_design_cache:";

	/** 首页页面设计锁key */
	String HOME_PAGE_DESIGN_LOCK_CACHE = HOME_PAGE_DESIGN_CACHE + "lock:";

	String ORDER_CONFIG_CACHE = "order_config_cache:";

}
