
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.sms.util.AliSmsUtils;
import com.aryn.cloud.upms.service.ISysSmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 短信
 *
 * <p>发送接口是免登的（`/sms/**` 在 `secure.ignore.urls` 白名单内），必须自己做频控，
 * 否则可被匿名调用刷短信（短信轰炸 / 费用滥用）。频控只作用于**真正会发短信**的
 * login/update 类型，按三级限制：</p>
 * <ol>
 *   <li>同手机号 60 秒冷却：`setIfAbsent` 原子占位，并发重复请求只放行一个；</li>
 *   <li>同手机号 5 次/小时；</li>
 *   <li>同 IP 20 次/小时（IP 由 {@code SysSmsController} 从请求头解析）。</li>
 * </ol>
 *
 * <p>验证码在 5 分钟有效期内时直接返回成功、不重发，也不消耗发送配额
 * （保持原行为：用户未收到短信时重试能拿到成功提示，但不会产生新短信）。
 * 注：IP 取 `X-Forwarded-For` 首跳，boot 直接暴露时该请求头可伪造，
 * 因此频控是纵深防御，不是唯一防线（验证码校验失败计数等仍待后续补）。</p>
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@RequiredArgsConstructor
public class SysSmsServiceImpl implements ISysSmsService {

	/** 同手机号发送冷却秒数 */
	private static final long SEND_COOLDOWN_SECONDS = 60L;

	/** 配额统计窗口（秒） */
	private static final long SEND_QUOTA_WINDOW_SECONDS = TimeUnit.HOURS.toSeconds(1);

	/** 同手机号每小时最多发送次数 */
	private static final long MAX_SENDS_PER_PHONE_PER_HOUR = 5L;

	/** 同 IP 每小时最多发送次数 */
	private static final long MAX_SENDS_PER_IP_PER_HOUR = 20L;

	/**
	 * 全局（实例/Redis 共享）每小时发送上限：给 IP 维度兜底。
	 * 网关/反代未正确覆写 `X-Forwarded-For`、或攻击者伪造 IP 头时，
	 * 手机号维度（每号 5 次）仍会被换号绕过，全局上限把整体成本暴露封顶。
	 */
	private static final long MAX_SENDS_GLOBAL_PER_HOUR = 300L;

	/** 验证码有效期（秒） */
	private static final long CODE_TTL_SECONDS = 300L;

	private final StringRedisTemplate redisTemplate;

	private final AliSmsUtils aliSmsUtils;

	@Override
	public Boolean sendSmsCode(String mobile, String type, String clientIp) {
		switch (type) {
			case "register":
				// 发送注册验证码（后端未实现，保持原行为）
				break;
			case "login":
			case "update":
				// 发送登录验证码 / 修改手机号验证码
				String codeKey = CacheConstants.SMS_CODE_KEY + mobile;
				String cachedCode = redisTemplate.opsForValue().get(codeKey);
				if (StringUtils.hasText(cachedCode)) {
					// 验证码未过期：不重发、不消耗发送配额
					return Boolean.TRUE;
				}
				assertSendAllowed(mobile, clientIp);
				String code = RandomUtil.randomNumbers(4);
				Map<String, String> maps = new HashMap<>();
				maps.put("code", code);
				try {
					aliSmsUtils.sendSms(mobile, JSONUtil.toJsonStr(maps));
				}
				catch (Exception e) {
					throw new RuntimeException(e.getMessage());
				}
				// 保存验证码信息
				redisTemplate.opsForValue().set(codeKey, code, CODE_TTL_SECONDS, TimeUnit.SECONDS);
				break;
			case "forget":
				// 发送忘记密码验证码（后端未实现，保持原行为）
				break;
			default:
		}
		return Boolean.TRUE;
	}

	/**
	 * 发送前频控：手机号冷却 → 手机号小时配额 → IP 小时配额。
	 * 命中限制抛 {@link ArynBusinessException}，由全局异常处理转成 `Result.fail(msg)`。
	 */
	private void assertSendAllowed(String mobile, String clientIp) {
		requireCooldown(CacheConstants.SMS_SEND_COOLDOWN_KEY + mobile);
		requireHourlyQuota(CacheConstants.SMS_SEND_PHONE_HOUR_KEY + mobile, MAX_SENDS_PER_PHONE_PER_HOUR,
				"该手机号验证码发送次数已达上限，请稍后再试");
		// 直连场景 remoteAddr 可能为空，为空时只保留手机号维度限制
		if (StringUtils.hasText(clientIp)) {
			requireHourlyQuota(CacheConstants.SMS_SEND_IP_HOUR_KEY + clientIp, MAX_SENDS_PER_IP_PER_HOUR,
					"请求过于频繁，请稍后再试");
		}
		// 全局兜底：即使 IP 头被伪造/换号，整体发送量也有上限
		requireHourlyQuota(CacheConstants.SMS_SEND_GLOBAL_HOUR_KEY, MAX_SENDS_GLOBAL_PER_HOUR,
				"短信发送过于频繁，请稍后再试");
	}

	private void requireCooldown(String key) {
		Boolean acquired = redisTemplate.opsForValue()
			.setIfAbsent(key, "1", SEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
		if (!Boolean.TRUE.equals(acquired)) {
			throw new ArynBusinessException("验证码发送过于频繁，请 " + SEND_COOLDOWN_SECONDS + " 秒后再试");
		}
	}

	private void requireHourlyQuota(String key, long max, String message) {
		Long count = redisTemplate.opsForValue().increment(key);
		if (count == null) {
			return;
		}
		// 每次自增都续期（滑动窗口）：即使某次 expire 调用丢失，也不会把 key 永久留在 Redis 里
		redisTemplate.expire(key, SEND_QUOTA_WINDOW_SECONDS, TimeUnit.SECONDS);
		if (count > max) {
			throw new ArynBusinessException(message);
		}
	}

}
