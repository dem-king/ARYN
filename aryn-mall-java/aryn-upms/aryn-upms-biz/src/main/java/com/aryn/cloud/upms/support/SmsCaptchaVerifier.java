package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.core.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 图形验证码（aj-captcha）二次校验。
 *
 * <p>短信发送是免登接口，发送前要求先通过前端滑块验证码。aj-captcha 的
 * `/code/check` 通过后会把一次性票据写入共享 Redis（key =
 * `RUNNING:CAPTCHA:second-<captchaVerification>`，TTL 3 分钟），
 * 业务侧校验该 key 是否存在并在命中后删除（一次性）。
 * 该 key 前缀与 aj-captcha 1.4.0 内部常量一致，由
 * `CaptchaSecondVerificationKeyContractTest` 反射比对守住。</p>
 *
 * <p>放在本服务（而非 auth）实现的原因：短信发送端点在 upms，cloud 模式下两者是独立服务，
 * 共享 Redis 才能跨服务校验；boot 单模式下同样成立。</p>
 */
@Component
@RequiredArgsConstructor
public class SmsCaptchaVerifier {

	private final StringRedisTemplate redisTemplate;

	/**
	 * 校验并消费一次性图形验证码票据。
	 * @param captchaVerification 前端 Verify 组件 success 回调返回的二次校验串
	 * @return true=校验通过且已消费；false=缺失、过期或已使用
	 */
	public boolean verifyAndConsume(String captchaVerification) {
		if (!StringUtils.hasText(captchaVerification)) {
			return false;
		}
		String key = CacheConstants.CAPTCHA_SECOND_VERIFICATION_KEY + captchaVerification.trim();
		if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
			return false;
		}
		// 一次性票据：校验通过立刻删除，防止同一张验证码被并发复用
		redisTemplate.delete(key);
		return true;
	}

}
