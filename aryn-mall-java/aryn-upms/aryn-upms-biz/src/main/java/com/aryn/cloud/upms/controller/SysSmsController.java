
package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.config.SmsRateLimitProperties;
import com.aryn.cloud.upms.service.ISysSmsService;
import com.aryn.cloud.upms.support.SmsCaptchaVerifier;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短信服务（发送验证码）。
 *
 * <p>该端点注册在免登白名单 `/sms/**` 内，因此必须自己完成准入控制：</p>
 * <ul>
 *   <li>`type=login`（管理端手机验证码登录）：要求先通过图形验证码（`captchaVerification`）；</li>
 *   <li>`type=update`（小程序换绑手机号）：要求已登录（换绑本身就是登录态操作）；</li>
 *   <li>频控见 `SysSmsServiceImpl`（同号 60s / 同号 5 次每小时 / 同 IP 20 次每小时 / 全局兜底）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sms")
@Tag(description = "sms", name = "短信服务")
public class SysSmsController {

	private final ISysSmsService sysSmsService;

	private final SmsCaptchaVerifier smsCaptchaVerifier;

	private final SmsRateLimitProperties smsRateLimitProperties;

	/**
	 * 发送短信验证码
	 * @param type 短信类型：login=登录 / update=换绑手机号（register、forget 后端暂未实现，不发短信）
	 * @param mobile 手机号
	 * @param captchaVerification 图形验证码二次校验串（type=login 必填）
	 * @return
	 */
	@Operation(summary = "发送短信")
	@GetMapping("/{type}/{mobile}")
	public Result<Boolean> send(@PathVariable String type, @PathVariable String mobile,
			@RequestParam(required = false) String captchaVerification, HttpServletRequest request) {
		if ("login".equals(type)) {
			if (!StringUtils.hasText(captchaVerification)) {
				return Result.fail("图形验证码不能为空");
			}
			if (!smsCaptchaVerifier.verifyAndConsume(captchaVerification)) {
				return Result.fail("图形验证码已失效，请重新验证");
			}
		}
		if ("update".equals(type) && !StpUtil.isLogin()) {
			return Result.fail(HttpStatus.UNAUTHORIZED.value(), "请先登录后再获取验证码");
		}
		return Result.success(sysSmsService.sendSmsCode(mobile, type,
				resolveClientIp(request, smsRateLimitProperties.isTrustForwardedHeaders())));
	}

	/**
	 * 解析客户端 IP 作为发送频控维度。
	 *
	 * <p>受信代理场景（cloud 网关）：`X-Forwarded-For` **取最后一跳**——网关会把真实客户端
	 * 地址追加到末尾，取首跳会被客户端伪造的头绕过；再退回 `X-Real-IP`、`remoteAddr`。
	 * boot 直连场景不信任任何代理头，直接使用 `remoteAddr`，避免伪造头绕过 IP 限流。</p>
	 */
	static String resolveClientIp(HttpServletRequest request, boolean trustForwardedHeaders) {
		if (trustForwardedHeaders) {
			String forwardedFor = request.getHeader("X-Forwarded-For");
			if (StringUtils.hasText(forwardedFor)) {
				String[] hops = forwardedFor.split(",");
				for (int i = hops.length - 1; i >= 0; i--) {
					String hop = hops[i].trim();
					if (StringUtils.hasText(hop) && !"unknown".equalsIgnoreCase(hop)) {
						return hop;
					}
				}
			}
			String realIp = request.getHeader("X-Real-IP");
			if (StringUtils.hasText(realIp) && !"unknown".equalsIgnoreCase(realIp)) {
				return realIp.trim();
			}
		}
		String remoteAddr = request.getRemoteAddr();
		return StringUtils.hasText(remoteAddr) ? remoteAddr : "";
	}

}
