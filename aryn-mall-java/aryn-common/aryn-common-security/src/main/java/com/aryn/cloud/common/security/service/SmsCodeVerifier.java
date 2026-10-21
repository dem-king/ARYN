package com.aryn.cloud.common.security.service;

import com.aryn.cloud.common.core.constant.CacheConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 短信验证码校验器（原子「校验 + 消费 + 失败计数」）。
 *
 * <p>4 位短信验证码在 5 分钟有效期内理论上可枚举，因此校验必须带失败次数上限：
 * 同一条验证码累计错误 {@value #MAX_ATTEMPTS} 次即作废（同时清掉计数），
 * 用户需要重新获取。校验、成功后删除、失败自增、超限作废全部在一个 Lua 脚本里完成，
 * 避免并发场景下「先查后删」被重放或多计。</p>
 *
 * <p>该组件放在 auth / user 均可用的公共安全模块，通过自动装配注入，
 * 供管理端/C 端短信登录过滤器与小程序换绑手机号接口共用。</p>
 */
public class SmsCodeVerifier {

	/** 单条验证码允许的最大错误次数 */
	public static final int MAX_ATTEMPTS = 5;

	/** 验证码兜底 TTL（秒），仅用于计数 key 在代码 key 无过期时间时也能过期 */
	private static final long FALLBACK_CODE_TTL_SECONDS = 300L;

	/**
	 * KEYS[1]=验证码缓存 key，KEYS[2]=失败次数 key；ARGV[1]=用户输入，ARGV[2]=最大错误次数。
	 * 返回值：1=校验通过且已消费；0=不匹配/已过期；-1=错误次数超限、验证码已作废。
	 */
	private static final RedisScript<Long> VERIFY_AND_CONSUME_SCRIPT = new DefaultRedisScript<>("""
			local cached = redis.call('GET', KEYS[1])
			if not cached then
				return 0
			end
			if cached == ARGV[1] then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[2])
				return 1
			end
			local attempts = redis.call('INCR', KEYS[2])
			local ttl = redis.call('TTL', KEYS[1])
			if ttl > 0 then
				redis.call('EXPIRE', KEYS[2], ttl)
			else
				redis.call('EXPIRE', KEYS[2], %d)
			end
			if attempts >= tonumber(ARGV[2]) then
				redis.call('DEL', KEYS[1])
				redis.call('DEL', KEYS[2])
				return -1
			end
			return 0
			""".formatted(FALLBACK_CODE_TTL_SECONDS), Long.class);

	public enum Result {

		/** 校验通过，验证码已消费 */
		SUCCESS,

		/** 验证码错误或已过期 */
		INVALID,

		/** 错误次数达到上限，验证码已作废 */
		TOO_MANY_ATTEMPTS

	}

	private final StringRedisTemplate redisTemplate;

	public SmsCodeVerifier(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public Result verifyAndConsume(String phone, String code) {
		if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
			return Result.INVALID;
		}
		Long result = redisTemplate.execute(VERIFY_AND_CONSUME_SCRIPT,
				List.of(CacheConstants.SMS_CODE_KEY + phone, CacheConstants.SMS_CODE_ATTEMPT_KEY + phone), code,
				String.valueOf(MAX_ATTEMPTS));
		if (result == null) {
			return Result.INVALID;
		}
		if (result == 1L) {
			return Result.SUCCESS;
		}
		if (result == -1L) {
			return Result.TOO_MANY_ATTEMPTS;
		}
		return Result.INVALID;
	}

}
