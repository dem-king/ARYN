package com.aryn.cloud.auth.filter;

import com.aryn.cloud.common.security.service.SmsCodeVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短信验证码过滤器契约。
 *
 * <p>{@code /token/**} 整体在免登白名单内（application.yml `secure.ignore.urls`），
 * 管理端短信登录必须在此自己校验验证码。历史实现只拦 {@code /token/phone/login}，
 * 而真实入口是 {@code /token/sms/login}，导致**知道管理员手机号即可匿名签发 TOB token**
 * （2026-10-21 修复）。这里按路径把两类短信登录都钉住，并锁住失败计数结果到用户提示的映射。</p>
 */
class SmsValidateCodeFilterTest {

	private static final String PHONE = "17615123399";

	private SmsCodeVerifier smsCodeVerifier;

	private SmsValidateCodeFilter filter;

	private HttpServletRequest request;

	private HttpServletResponse response;

	private FilterChain chain;

	private StringWriter responseBody;

	@BeforeEach
	void setUp() throws Exception {
		smsCodeVerifier = mock(SmsCodeVerifier.class);
		filter = new SmsValidateCodeFilter(smsCodeVerifier);
		request = mock(HttpServletRequest.class);
		response = mock(HttpServletResponse.class);
		chain = mock(FilterChain.class);
		responseBody = new StringWriter();
		when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
	}

	@Test
	void tobSmsLoginWithoutCodeIsRejected() throws Exception {
		givenRequest("/token/sms/login", PHONE, null);

		invokeFilter();

		// 修复前：该请求会直接签发管理员 TOB token
		assertThat(responseBody.toString()).contains("验证码为空");
		assertThat(responseBody.toString()).doesNotContain("tokenValue");
		verifyNoInteractions(chain);
		verifyNoInteractions(smsCodeVerifier);
	}

	@Test
	void tobSmsLoginWithInvalidCodeIsRejected() throws Exception {
		givenRequest("/token/sms/login", PHONE, "1234");
		when(smsCodeVerifier.verifyAndConsume(PHONE, "1234")).thenReturn(SmsCodeVerifier.Result.INVALID);

		invokeFilter();

		assertThat(responseBody.toString()).contains("验证码不合法");
		verifyNoInteractions(chain);
	}

	@Test
	void tobSmsLoginWithTooManyAttemptsIsRejectedDistinctly() throws Exception {
		givenRequest("/token/sms/login", PHONE, "0000");
		when(smsCodeVerifier.verifyAndConsume(PHONE, "0000"))
			.thenReturn(SmsCodeVerifier.Result.TOO_MANY_ATTEMPTS);

		invokeFilter();

		assertThat(responseBody.toString()).contains("验证码错误次数过多，请重新获取验证码");
		verifyNoInteractions(chain);
	}

	@Test
	void tobSmsLoginWithValidCodePasses() throws Exception {
		givenRequest("/token/sms/login", PHONE, "1234");
		when(smsCodeVerifier.verifyAndConsume(PHONE, "1234")).thenReturn(SmsCodeVerifier.Result.SUCCESS);

		invokeFilter();

		verify(chain).doFilter(request, response);
		assertThat(responseBody.toString()).isEmpty();
	}

	@Test
	void tocSmsLoginKeepsSameGuard() throws Exception {
		givenRequest("/toc-token/sms/login", PHONE, "1234");
		when(smsCodeVerifier.verifyAndConsume(PHONE, "1234")).thenReturn(SmsCodeVerifier.Result.SUCCESS);

		invokeFilter();

		verify(chain).doFilter(request, response);
	}

	@Test
	void registerEndpointAlsoGuarded() throws Exception {
		givenRequest("/tenant/register", PHONE, "9999");
		when(smsCodeVerifier.verifyAndConsume(PHONE, "9999")).thenReturn(SmsCodeVerifier.Result.INVALID);

		invokeFilter();

		assertThat(responseBody.toString()).contains("验证码不合法");
		verifyNoInteractions(chain);
	}

	@Test
	void passwordLoginIsNotHandledByThisFilter() throws Exception {
		givenRequest("/token/login", PHONE, null);

		invokeFilter();

		// 管理端密码登录由 ValidateCodeFilter（图形验证码）负责，这里只放行、不查短信码
		verify(chain).doFilter(request, response);
		verify(smsCodeVerifier, never()).verifyAndConsume(PHONE, null);
	}

	private void givenRequest(String servletPath, String phone, String code) {
		when(request.getServletPath()).thenReturn(servletPath);
		when(request.getParameter("phone")).thenReturn(phone);
		when(request.getParameter("code")).thenReturn(code);
	}

	private void invokeFilter() throws Exception {
		filter.doFilterInternal(request, response, chain);
	}

}
