package com.aryn.cloud.common.log.aop;

import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.entity.SysLogBase;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.log.event.ArynLogEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 操作日志切面：记录管理端写操作（标题/URI/操作人/租户/耗时/参数摘要）。
 *
 * <p>2026-11-04 注册为 Bean（此前仅 @Aspect 未注册，导致 sys_log 一直为空）；
 * 参数摘要做敏感字段脱敏并截断，避免密码/令牌与大请求体落库。
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LogAopAspect {

	private static final int MAX_PARAMS_LENGTH = 2000;

	private static final Pattern SENSITIVE_FIELD = Pattern.compile(
			"(?i)(\"(?:password|oldPassword|newPassword|confirmPassword|token|accessToken|refreshToken|secret|secretKey|mchKey|apiv3Key|privateKey|privateKeyPath|privateCertPath|publicKeyPath|keyPath|certSerialNo|ciphertext|nonce)\"\\s*:\\s*\")[^\"]*(\")");

	private static final String BINARY_OMITTED = "[binary omitted]";

	private final ApplicationEventPublisher applicationEventPublisher;

	private final com.aryn.cloud.common.core.security.UserSupplier userSupplier;

	@SneakyThrows
	@Around("@annotation(sysLog)")
	public Object around(ProceedingJoinPoint point, com.aryn.cloud.common.log.annotation.SysLog sysLog) {
		HttpServletRequest request = ((ServletRequestAttributes) Objects
			.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
		MethodSignature methodSignature = (MethodSignature) point.getSignature();
		Method method = methodSignature.getMethod();
		SysLog log = method.getAnnotation(SysLog.class);
		String title = log.value();
		SysLogBase sysLogVo = new SysLogBase();
		String ip = IpUtils.getClientIp(request);
		sysLogVo.setMethod(request.getMethod());
		sysLogVo.setIpAddr(ip);
		sysLogVo.setLocation(IpUtils.getWhoisAddress(ip));
		sysLogVo.setRequestMethod(point.getSignature().getDeclaringTypeName() + "." + point.getSignature().getName());
		sysLogVo.setRequestUri(URLUtil.getPath(request.getRequestURI()));
		sysLogVo.setTitle(title);
		sysLogVo.setStatus(CommonConstants.LOGIN_LOG_STATUS_1);
		sysLogVo.setUserName(userSupplier.getCurrentUserName());
		sysLogVo.setTenantId(ArynTenantContextHolder.getTenantId());
		sysLogVo.setRequestParams(safeParams(point.getArgs()));

		Long startTime = System.currentTimeMillis();
		Object result = null;
		try {
			result = point.proceed();

		}
		catch (Exception e) {
			sysLogVo.setStatus(CommonConstants.LOGIN_LOG_STATUS_0);
			sysLogVo.setExMsg(e.getMessage());
			throw e;
		}
		finally {
			Long endTime = System.currentTimeMillis();
			sysLogVo.setRequestTime(endTime - startTime);
			applicationEventPublisher.publishEvent(new ArynLogEvent(this, sysLogVo));

		}
		return result;
	}

	/**
	 * 参数摘要：先剔除文件/字节流参数，再 JSON 序列化、敏感字段脱敏与长度截断；
	 * 任何异常都不影响主流程。支付资料上传必须在这里被拦住，否则私钥会进入 sys_log。
	 */
	String safeParams(Object[] args) {
		if (args == null || args.length == 0) {
			return null;
		}
		try {
			Object[] sanitizedArgs = new Object[args.length];
			for (int index = 0; index < args.length; index++) {
				sanitizedArgs[index] = sanitizeArgument(args[index]);
			}
			String json = JSONUtil.toJsonStr(sanitizedArgs);
			if (json == null) {
				return null;
			}
			json = SENSITIVE_FIELD.matcher(json).replaceAll("$1***$2");
			return json.length() > MAX_PARAMS_LENGTH ? json.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)"
					: json;
		}
		catch (Exception ex) {
			log.debug("操作日志参数序列化失败，已忽略", ex);
			return null;
		}
	}

	private Object sanitizeArgument(Object argument) {
		if (argument == null) {
			return null;
		}
		if (argument instanceof byte[] || argument instanceof java.io.InputStream) {
			return BINARY_OMITTED;
		}
		for (Class<?> interfaceType : argument.getClass().getInterfaces()) {
			if ("org.springframework.web.multipart.MultipartFile".equals(interfaceType.getName())) {
				return BINARY_OMITTED;
			}
		}
		return argument;
	}

}
