package com.aryn.cloud.auth.service;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.auth.config.LoginProtectionProperties;
import com.aryn.cloud.common.core.constant.CacheConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 密码登录失败限流 / 临时锁定。
 *
 * <p>背景：管理端密码登录、配送员登录都复用系统员工账号密码，此前没有账号或 IP 维度的
 * 失败计数与锁定，在线暴力破解与撞库没有成本。这里用 Redis 原子计数实现两级滑动窗口：</p>
 * <ol>
 *   <li><b>账号维度</b>：同一账号在窗口内失败达到上限后锁定（默认 5 次 / 15 分钟）；</li>
 *   <li><b>IP 维度</b>：同一出口 IP 在窗口内失败达到上限后锁定（默认 30 次 / 15 分钟）。</li>
 * </ol>
 *
 * <p>注意：<b>不区分账号是否存在</b>，不存在的账号同样计数、同样返回统一错误提示，
 * 避免通过「锁定行为/错误文案」枚举有效账号。登录成功后只清账号维度计数，
 * 不清 IP 维度，防止攻击者用少量有效账号反复重置 IP 限制。</p>
 *
 * <p>计数与过期在 Lua 中原子完成，避免并发下「先查后写」漏计。Redis 不可用时与
 * Sa-Token 会话存储同源，登录本身已不可用，故不做降级放行。</p>
 *
 * @author aryn
 * @since 2026/10/28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {

	/**
	 * KEYS[1]=账号失败计数，KEYS[2]=IP 失败计数；ARGV[1]=账号上限，ARGV[2]=IP 上限，
	 * ARGV[3]=账号锁定兜底秒数，ARGV[4]=是否启用 IP 维度（'1'/'0'），ARGV[5]=IP 锁定兜底秒数。
	 * 返回 0=放行；>0=锁定剩余秒数。
	 */
	private static final RedisScript<Long> CHECK_SCRIPT = new DefaultRedisScript<>("""
			local accountFails = tonumber(redis.call('GET', KEYS[1]) or '0')
			if accountFails >= tonumber(ARGV[1]) then
				local ttl = redis.call('TTL', KEYS[1])
				if ttl < 0 then ttl = tonumber(ARGV[3]) end
				return ttl
			end
			if ARGV[4] == '1' then
				local ipFails = tonumber(redis.call('GET', KEYS[2]) or '0')
				if ipFails >= tonumber(ARGV[2]) then
					local ttl = redis.call('TTL', KEYS[2])
					if ttl < 0 then ttl = tonumber(ARGV[5]) end
					return ttl
				end
			end
			return 0
			""", Long.class);

	/**
	 * KEYS[1]=账号失败计数，KEYS[2]=IP 失败计数；ARGV[1]=账号计数 TTL 秒数，
	 * ARGV[2]=是否启用 IP 维度（'1'/'0'），ARGV[3]=IP 计数 TTL 秒数。
	 * 每次失败都滑动续期：持续尝试会持续锁定，而不是固定窗口内的「充值」。
	 */
	private static final RedisScript<Long> RECORD_FAILURE_SCRIPT = new DefaultRedisScript<>("""
			local accountCount = redis.call('INCR', KEYS[1])
			redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
			if ARGV[2] == '1' then
				redis.call('INCR', KEYS[2])
				redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
			end
			return accountCount
			""", Long.class);

	private final StringRedisTemplate redisTemplate;

	private final LoginProtectionProperties properties;

	/**
	 * 判断当前登录尝试是否已被锁定。
	 * @param account 登录账号（用户名或手机号，方法内部统一小写归一）
	 * @param clientIp 客户端 IP，为空时只检查账号维度
	 * @return 0 表示允许继续；大于 0 表示锁定剩余秒数
	 */
	public long retryAfterSeconds(String account, String clientIp) {
		if (!properties.isEnabled() || StrUtil.isBlank(account)) {
			return 0L;
		}
		boolean ipEnabled = StrUtil.isNotBlank(clientIp);
		List<String> keys = new ArrayList<>(2);
		keys.add(accountKey(account));
		keys.add(ipEnabled ? ipKey(clientIp) : accountKey(account));
		try {
			Long ttl = redisTemplate.execute(CHECK_SCRIPT, keys,
					String.valueOf(properties.getMaxAccountFailures()),
					String.valueOf(properties.getMaxIpFailures()),
					String.valueOf(properties.getAccountLockSeconds()),
					ipEnabled ? "1" : "0",
					String.valueOf(properties.getIpLockSeconds()));
			return ttl == null || ttl <= 0 ? 0L : ttl;
		}
		catch (Exception e) {
			// 计数链路异常不能把攻击者放进来：Redis 与 Sa-Token 同源，此时登录本就不可用；
			// 返回一个窗口值让调用方按「稍后再试」处理，避免异常直接 500 暴露内部堆栈。
			log.error("登录失败计数检查异常，按限流处理 account={}", mask(account), e);
			return properties.getAccountLockSeconds();
		}
	}

	/**
	 * 记录一次登录失败（账号不存在、密码错误、密码为空等统一计入）。
	 */
	public void recordFailure(String account, String clientIp) {
		if (!properties.isEnabled() || StrUtil.isBlank(account)) {
			return;
		}
		boolean ipEnabled = StrUtil.isNotBlank(clientIp);
		List<String> keys = new ArrayList<>(2);
		keys.add(accountKey(account));
		keys.add(ipEnabled ? ipKey(clientIp) : accountKey(account));
		try {
			redisTemplate.execute(RECORD_FAILURE_SCRIPT, keys,
					String.valueOf(properties.getAccountLockSeconds()),
					ipEnabled ? "1" : "0",
					String.valueOf(properties.getIpLockSeconds()));
		}
		catch (Exception e) {
			// 失败计数写不进去时只告警：密码校验已经失败，本次登录仍会被拒绝
			log.warn("登录失败计数写入异常 account={}", mask(account), e);
		}
	}

	/**
	 * 登录成功后清空账号维度失败计数。
	 */
	public void resetAccount(String account) {
		if (StrUtil.isBlank(account)) {
			return;
		}
		try {
			redisTemplate.delete(accountKey(account));
		}
		catch (Exception e) {
			log.warn("登录成功计数清理异常 account={}", mask(account), e);
		}
	}

	static String accountKey(String account) {
		return CacheConstants.LOGIN_FAIL_ACCOUNT_KEY
				+ (account == null ? "" : account.trim().toLowerCase(Locale.ROOT));
	}

	static String ipKey(String clientIp) {
		return CacheConstants.LOGIN_FAIL_IP_KEY + (clientIp == null ? "" : clientIp.trim());
	}

	/** 日志里不落完整账号/手机号 */
	private static String mask(String value) {
		if (value == null || value.length() <= 2) {
			return "**";
		}
		return value.charAt(0) + "***" + value.charAt(value.length() - 1);
	}

}
