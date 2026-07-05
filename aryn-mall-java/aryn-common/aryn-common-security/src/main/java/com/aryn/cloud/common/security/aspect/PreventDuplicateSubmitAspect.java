
package com.aryn.cloud.common.security.aspect;

import com.aryn.cloud.common.security.annotation.PreventDuplicateSubmit;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 防重提交 AOP 切面
 *
 * <p>基于 Redis SETNX 实现，key 由 用户ID + 请求URI + 参数哈希 组成。
 *
 * @author aryn
 * @date 2026/7/4
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PreventDuplicateSubmitAspect {

	private final RedisTemplate<String, Object> redisTemplate;

	private static final String KEY_PREFIX = "prevent:submit:";

	@Around("@annotation(preventDuplicateSubmit)")
	public Object around(ProceedingJoinPoint joinPoint, PreventDuplicateSubmit preventDuplicateSubmit)
			throws Throwable {
		// 构建去重 key
		String key = buildKey(joinPoint);

		// 尝试获取锁
		Boolean absent = redisTemplate.opsForValue().setIfAbsent(key, "1",
				Duration.ofSeconds(preventDuplicateSubmit.interval()));

		if (Boolean.FALSE.equals(absent)) {
			log.warn("重复提交被拦截: key={}", key);
			throw new ArynBusinessException(preventDuplicateSubmit.message());
		}

		return joinPoint.proceed();
	}

	/**
	 * 构建去重 key：用户ID + 请求URI + 参数哈希
	 */
	private String buildKey(ProceedingJoinPoint joinPoint) {
		StringBuilder sb = new StringBuilder(KEY_PREFIX);

		// 用户ID
		try {
			String userId = SecurityUtils.getUserId();
			sb.append(userId);
		}
		catch (Exception e) {
			sb.append("anonymous");
		}

		// 请求URI
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
			.getRequestAttributes();
		if (attributes != null) {
			HttpServletRequest request = attributes.getRequest();
			sb.append(":").append(request.getRequestURI());
		}

		// 参数哈希（简单使用 toString 的哈希值）
		Object[] args = joinPoint.getArgs();
		if (args != null && args.length > 0) {
			AtomicReference<String> argsStr = new AtomicReference<>("");
			for (Object arg : args) {
				if (arg != null) {
					argsStr.set(argsStr.get() + arg.hashCode());
				}
			}
			sb.append(":").append(argsStr.get());
		}

		return sb.toString();
	}

}
