package com.aryn.cloud.common.log.aop;

import cn.hutool.core.util.URLUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Objects;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class LogAopAspect {

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
		String ip = JakartaServletUtil.getClientIP(request);
		sysLogVo.setMethod(request.getMethod());
		sysLogVo.setIpAddr(ip);
		sysLogVo.setLocation(IpUtils.getWhoisAddress(ip));
		sysLogVo.setRequestMethod(point.getSignature().getDeclaringTypeName() + "." + point.getSignature().getName());
		sysLogVo.setRequestUri(URLUtil.getPath(request.getRequestURI()));
		sysLogVo.setTitle(title);
		sysLogVo.setStatus(CommonConstants.LOGIN_LOG_STATUS_1);
		sysLogVo.setUserName(userSupplier.getCurrentUserName());
		sysLogVo.setTenantId(ArynTenantContextHolder.getTenantId());

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

}
