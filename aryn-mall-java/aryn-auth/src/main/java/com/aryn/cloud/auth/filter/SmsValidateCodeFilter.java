
package com.aryn.cloud.auth.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.SecurityConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.service.SmsCodeVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 短信验证码校验过滤器。
 *
 * <p>覆盖三类免登短信验证码入口：管理端短信登录 {@code /token/sms/login}、
 * C 端短信登录 {@code /toc-token/sms/login}、租户注册 {@code /tenant/register}、
 * 以及历史常量 {@code /token/phone/login}。</p>
 *
 * <p>校验统一走 {@link SmsCodeVerifier}：原子完成「比对 + 一次性消费 + 失败计数」，
 * 同一条验证码错误 5 次即作废，避免 4 位码在 5 分钟有效期内被枚举。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsValidateCodeFilter extends OncePerRequestFilter {

	private final SmsCodeVerifier smsCodeVerifier;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		// 验证码校验逻辑
		// 注意：/token/** 全部在免登白名单内（见 application.yml secure.ignore.urls），
		// 管理端短信登录 /token/sms/login 必须和 C 端 /toc-token/sms/login 一样在这里校验验证码，
		// 否则仅凭手机号就能签发 TOB token。
		if (!StrUtil.containsAnyIgnoreCase(request.getServletPath(), SecurityConstants.PHONE_SMS_TOKEN,
				SecurityConstants.TOB_SMS_TOKEN, SecurityConstants.REGISTER,
				SecurityConstants.TOC_PHONE_SMS_TOKEN)) {
			filterChain.doFilter(request, response);
			return;
		}
		String code = request.getParameter("code");
		if (StrUtil.isBlank(code)) {
			writeFailure(response, "验证码为空");
			return;
		}
		String phone = request.getParameter("phone");
		SmsCodeVerifier.Result result = smsCodeVerifier.verifyAndConsume(phone, code);
		switch (result) {
			case SUCCESS -> filterChain.doFilter(request, response);
			case TOO_MANY_ATTEMPTS -> writeFailure(response, "验证码错误次数过多，请重新获取验证码");
			default -> writeFailure(response, "验证码不合法");
		}
	}

	/**
	 * 过滤器在 DispatcherServlet 之前执行，抛异常会绕过全局异常处理、返回裸 500；
	 * 这里按项目既有出参约定写 `Result` JSON（HTTP 200 + 业务码），
	 * 前端请求层才能把校验失败原因原样提示给用户。
	 */
	private void writeFailure(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().print(JSONUtil.toJsonStr(Result.fail(message)));
	}

}
