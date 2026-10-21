package com.aryn.cloud.auth.captcha;

import com.anji.captcha.service.impl.AbstractCaptchaService;
import com.aryn.cloud.common.core.constant.CacheConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图形验证码二次校验 key 契约。
 *
 * <p>短信发送端点（upms）与 auth 在 cloud 模式下是两个独立服务，靠共享 Redis 里的
 * aj-captcha 二次校验票据跨服务校验；票据 key 由其内部常量
 * `REDIS_SECOND_CAPTCHA_KEY` 决定。我们复刻了前缀（{@code CacheConstants.CAPTCHA_SECOND_VERIFICATION_KEY}），
 * 这里反射比对库常量：升级 aj-captcha 若改了 key 格式，本测试会先失败，
 * 避免线上短信发送验证码静默失效。</p>
 */
class CaptchaSecondVerificationKeyContractTest {

	@Test
	void secondVerificationKeyPrefixMatchesAjCaptchaLibraryConstant() throws Exception {
		Field field = AbstractCaptchaService.class.getDeclaredField("REDIS_SECOND_CAPTCHA_KEY");
		field.setAccessible(true);
		String libraryKeyFormat = String.valueOf(field.get(null));

		assertThat(CacheConstants.CAPTCHA_SECOND_VERIFICATION_KEY + "%s").isEqualTo(libraryKeyFormat);
	}

}
