package com.aryn.cloud.common.security.config;

import com.aryn.cloud.common.security.service.SmsCodeVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 短信验证码校验器自动装配。
 *
 * <p>auth（管理端/C 端短信登录过滤器）、user（小程序换绑手机号）都需要
 * 「校验 + 一次性消费 + 失败计数」，放在 common-security 里统一提供，
 * 避免两处各写一份 Lua 脚本出现口径漂移。</p>
 */
@Configuration
public class SmsCodeVerifyConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public SmsCodeVerifier smsCodeVerifier(StringRedisTemplate redisTemplate) {
		return new SmsCodeVerifier(redisTemplate);
	}

}
