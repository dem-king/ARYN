package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.config.SmsRateLimitProperties;
import com.aryn.cloud.upms.service.ISysSmsService;
import com.aryn.cloud.upms.support.SmsCaptchaVerifier;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短信发送端点契约：准入控制 + 客户端 IP 解析（频控维度）。
 *
 * <p>该端点免登可调，准入必须自己把控：</p>
 * <ul>
 *   <li>`type=login` 必须带图形验证码二次校验串，且票据一次性消费；</li>
 *   <li>`type=update` 是登录态换绑流程，未登录直接拒绝；</li>
 *   <li>IP 解析：受信代理（cloud 网关）取 XFF 最后一跳；boot 直连不信任任何代理头。</li>
 * </ul>
 */
class SysSmsControllerTest {

	private static final String MOBILE = "13800000000";

	private static final String VERIFICATION = "captcha-ticket-1";

	private ISysSmsService sysSmsService;

	private SmsCaptchaVerifier smsCaptchaVerifier;

	private SmsRateLimitProperties rateLimitProperties;

	private SysSmsController controller;

	private HttpServletRequest request;

	@BeforeEach
	void setUp() {
		sysSmsService = mock(ISysSmsService.class);
		smsCaptchaVerifier = mock(SmsCaptchaVerifier.class);
		rateLimitProperties = new SmsRateLimitProperties();
		controller = new SysSmsController(sysSmsService, smsCaptchaVerifier, rateLimitProperties);
		request = mock(HttpServletRequest.class);
		when(request.getRemoteAddr()).thenReturn("10.0.0.9");
	}

	@Test
	void loginWithoutCaptchaVerificationIsRejected() {
		Result<Boolean> result = controller.send("login", MOBILE, null, request);

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).contains("图形验证码不能为空");
		verifyNoInteractions(sysSmsService, smsCaptchaVerifier);
	}

	@Test
	void loginWithExpiredCaptchaTicketIsRejected() {
		when(smsCaptchaVerifier.verifyAndConsume(VERIFICATION)).thenReturn(false);

		Result<Boolean> result = controller.send("login", MOBILE, VERIFICATION, request);

		assertThat(result.getCode()).isEqualTo(1);
		assertThat(result.getMsg()).contains("图形验证码已失效");
		verify(sysSmsService, never()).sendSmsCode(anyString(), anyString(), anyString());
	}

	@Test
	void loginWithValidCaptchaSendsSmsWithResolvedIp() {
		when(smsCaptchaVerifier.verifyAndConsume(VERIFICATION)).thenReturn(true);
		when(sysSmsService.sendSmsCode(MOBILE, "login", "10.0.0.9")).thenReturn(true);

		Result<Boolean> result = controller.send("login", MOBILE, VERIFICATION, request);

		assertThat(result.getCode()).isZero();
		verify(sysSmsService).sendSmsCode(MOBILE, "login", "10.0.0.9");
	}

	@Test
	void updateWithoutLoginIsRejected() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::isLogin).thenReturn(false);

			Result<Boolean> result = controller.send("update", MOBILE, null, request);

			assertThat(result.getCode()).isEqualTo(401);
			assertThat(result.getMsg()).contains("请先登录");
			verifyNoInteractions(sysSmsService);
		}
	}

	@Test
	void updateWithLoginSendsSms() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::isLogin).thenReturn(true);
			when(sysSmsService.sendSmsCode(MOBILE, "update", "10.0.0.9")).thenReturn(true);

			Result<Boolean> result = controller.send("update", MOBILE, null, request);

			assertThat(result.getCode()).isZero();
			verify(sysSmsService).sendSmsCode(MOBILE, "update", "10.0.0.9");
		}
	}

	@Test
	void untrustedProxyModeIgnoresForwardedHeaders() {
		when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
		when(request.getRemoteAddr()).thenReturn("10.0.0.9");

		assertThat(SysSmsController.resolveClientIp(request, false)).isEqualTo("10.0.0.9");
	}

	@Test
	void trustedProxyModeTakesLastForwardedHop() {
		// 首跳可被客户端伪造，最后一跳是网关追加的真实客户端地址
		when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 1.2.3.4");

		assertThat(SysSmsController.resolveClientIp(request, true)).isEqualTo("1.2.3.4");
	}

	@Test
	void trustedProxyModeFallsBackToRealIpThenRemoteAddr() {
		when(request.getHeader("X-Forwarded-For")).thenReturn(null);
		when(request.getHeader("X-Real-IP")).thenReturn("5.6.7.8");
		assertThat(SysSmsController.resolveClientIp(request, true)).isEqualTo("5.6.7.8");

		when(request.getHeader("X-Real-IP")).thenReturn("unknown");
		when(request.getRemoteAddr()).thenReturn("10.0.0.9");
		assertThat(SysSmsController.resolveClientIp(request, true)).isEqualTo("10.0.0.9");
	}

	@Test
	void trustedProxyModeSkipsBlankTailHops() {
		when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, , unknown");

		assertThat(SysSmsController.resolveClientIp(request, true)).isEqualTo("1.2.3.4");
	}

}
