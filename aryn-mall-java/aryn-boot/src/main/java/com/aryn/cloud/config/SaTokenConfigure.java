
package com.aryn.cloud.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.properties.CorsProperties;
import com.aryn.cloud.common.core.util.SessionInvalidResponse;
import com.aryn.cloud.common.security.support.SessionInvalidSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * [Sa-Token 权限认证] 配置类
 *
 * @author 雨滴kian
 * @date 2022/6/28
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SaTokenConfigure implements WebMvcConfigurer {

	private final PermitAllUrlProperties permitAllUrlProperties;

	private final CorsProperties corsProperties;

	/**
	 * 注册 [Sa-Token 全局过滤器]
	 */
	@Bean
	public SaServletFilter getSaServletFilter() {
		return new SaServletFilter()

			// 指定 [拦截路由] 与 [放行路由]
			.addInclude("/**")
			.addExclude("/favicon.ico")

			// 认证函数: 每次请求执行
			.setAuth(obj -> {
				List<String> ignoreUrls = permitAllUrlProperties.getUrls();
				// 登录认证：除白名单路径外均需要登录认证
				SaRouter.match("/**").notMatch(ignoreUrls).check(StpUtil::checkLogin);
			})

			// 异常处理函数：每次认证函数发生异常时执行此函数
			.setError(e -> {
				// 会话失效：下发机器可读原因（+ 被顶下线的新设备信息），msg 为脱敏固定文案，
				// 不再回显 Sa-Token 异常 message 里可能拼上的完整 token。
				if (e instanceof NotLoginException notLoginException) {
					SessionInvalidReason reason = SessionInvalidReason.fromSaTokenType(notLoginException.getType());
					// 注意：不能返回 SaResult —— 过滤器用 String.valueOf() 写响应，
					// SaResult#toString 不会把嵌套 data 序列化成 JSON（会输出 {reason=NOT_TOKEN}）。
					SaHolder.getResponse().setHeader("Content-Type", SessionInvalidResponse.CONTENT_TYPE_JSON);
					return SessionInvalidResponse.toJson(reason, SessionInvalidSupport.resolveData(reason));
				}
				return SaResult.error(e.getMessage()).setCode(HttpStatus.UNAUTHORIZED.value());
			})

			// 前置函数：在每次认证函数之前执行
			.setBeforeAuth(obj -> {
				String origin = SaHolder.getRequest().getHeader("Origin");
				if (corsProperties.isAllowedOrigin(origin)) {
					SaHolder.getResponse()
						.setHeader("Access-Control-Allow-Origin", origin)
						.setHeader("Vary", "Origin")
						.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
						.setHeader("Access-Control-Allow-Headers", corsProperties.getAllowedHeadersValue())
						.setHeader("Access-Control-Max-Age", "3600");
				}

				// 如果是预检请求，则立即返回到前端
				SaRouter.match(SaHttpMethod.OPTIONS).free(r -> log.debug("OPTIONS预检请求，不做处理")).back();
			});
	}

	/**
	 * 注册Sa-Token的注解拦截器，打开注解式鉴权功能
	 * @param registry
	 * @author 雨滴kian
	 * @date 2022/7/16
	 * @return: void
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 注册注解拦截器，并排除不需要注解鉴权的接口地址 (与登录拦截器无关)
		registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
	}

}
