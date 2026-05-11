
package com.aryn.cloud.common.security.aspect;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.SecurityConstants;
import com.aryn.cloud.common.security.annotation.ArynInner;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * 内部接口调用处理 aop
 *
 * @author 雨滴kian
 * @date 2022/6/28
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class ArynInnerAopAspect {

	private final HttpServletRequest request;

	@SneakyThrows
	@Around("@annotation(com.aryn.cloud.common.security.annotation.ArynInner)")
	public Object around(ProceedingJoinPoint point) {
		// 获取方法签名
		MethodSignature methodSignature = (MethodSignature) point.getSignature();
		// 获取方法
		Method method = methodSignature.getMethod();
		// 获取方法上面的注解
		ArynInner hxInner = method.getAnnotation(ArynInner.class);
		String header = request.getHeader(SecurityConstants.SOURCE);
		if (hxInner.value() && !StrUtil.equals(SecurityConstants.SOURCE_IN, header)) {
			log.warn("访问接口 {} 没有权限", point.getSignature().getName());
			throw new ArynBusinessException("无权访问");
		}
		return point.proceed();
	}

}
