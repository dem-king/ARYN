
package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.auth.service.LoginTenantGuard;
import com.aryn.cloud.auth.service.TocLoginService;
import com.aryn.cloud.common.core.constant.MallCommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商城用户端授权登录
 *
 * @author 雨滴kian
 * @since 2024/5/6 13:37
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/toc-token")
@Tag(description = "toc-token", name = "商城用户端授权登录")
public class TocTokenController {

	private final TocLoginService tocLoginService;

	private final LoginTenantGuard loginTenantGuard;

	@Operation(summary = "小程序登录")
	@PostMapping("/ma/login")
	public Result<Object> maLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		applyRequestIdentity(request, userLoginReqDTO);
		return Result.success(tocLoginService.maLogin(userLoginReqDTO));
	}

	@Operation(summary = "小程序手机号一键登录")
	@PostMapping("/ma/phone/login")
	public Result<SaTokenInfo> maPhoneLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		applyRequestIdentity(request, userLoginReqDTO);
		return Result.success(tocLoginService.maPhoneLogin(userLoginReqDTO));
	}

	@Operation(summary = "短信验证码登录")
	@PostMapping("/sms/login")
	public Result<SaTokenInfo> smsLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		applyRequestIdentity(request, userLoginReqDTO);
		return Result.success(tocLoginService.smsLogin(userLoginReqDTO));
	}

	@Operation(summary = "密码登录")
	@PostMapping("/password/login")
	public Result<SaTokenInfo> passwordLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		applyRequestIdentity(request, userLoginReqDTO);
		return Result.success(tocLoginService.passwordLogin(userLoginReqDTO));
	}

	/**
	 * 每个登录入口无条件从原始请求头覆盖身份字段：禁止 body 提供的
	 * requestTenantId/appId/platformType 绕过校验，也禁止拿 ThreadLocal
	 * 值当原始请求头（有 token 时过滤器会改用会话租户）。
	 */
	/**
	 * 包内可见以便契约测试直接断言覆盖行为。
	 */
	void applyRequestIdentity(HttpServletRequest request, UserLoginReqDTO userLoginReqDTO) {
		userLoginReqDTO.setRequestTenantId(request.getHeader(com.aryn.cloud.common.core.constant.CommonConstants.TENANT_ID));
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
	}

	/**
	 * 小程序 AppID ↔ 租户绑定预检（匿名）。
	 *
	 * 复用权威绑定解析；公开返回仅 { appId, tenantId, ready }，不暴露 accountId/配置指纹/secret。
	 * 403 表示配置错误（不清 token、不跳登录）；RPC/DB 超时按异常透传，不把异常当 ready。
	 */
	@Operation(summary = "小程序租户绑定预检")
	@GetMapping("/tenant-binding")
	public Result<Map<String, Object>> tenantBinding(HttpServletRequest request) {
		String appId = request.getHeader(MallCommonConstants.HEADER_APP_ID);
		String tenantId = request.getHeader(com.aryn.cloud.common.core.constant.CommonConstants.TENANT_ID);
		if (StrUtil.isBlank(appId) || StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException(403, "缺少 app-id 或 tenant-id 请求头");
		}
		loginTenantGuard.requireVerifiedTenant(appId, tenantId);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("appId", appId);
		payload.put("tenantId", tenantId);
		payload.put("ready", Boolean.TRUE);
		return Result.success(payload);
	}

	/**
	 * 登录会话租户校验（scope=mall|delivery）。
	 *
	 * /toc-token/** 在免登白名单内，因此本端点在 Controller 显式校验登录：
	 * 无 token/过期/会话缺失一律转 401；设备类型（mall=TOC，delivery=TOB）与
	 * 会话租户（严格比对原始 tenant-id 头）错配一律 403，保留 token 不清不换。
	 */
	@Operation(summary = "登录会话租户校验")
	@GetMapping("/tenant-session")
	public Result<Map<String, Object>> tenantSession(HttpServletRequest request,
			@RequestParam("scope") String scope) {
		if (!"mall".equals(scope) && !"delivery".equals(scope)) {
			throw new ArynBusinessException(403, "scope 仅支持 mall 或 delivery");
		}
		ArynUser user;
		try {
			user = SecurityUtils.getUser();
		}
		catch (Exception exception) {
			throw new ArynBusinessException(401, "会话不存在");
		}
		if (user == null || !StpUtil.isLogin()) {
			throw new ArynBusinessException(401, "会话不存在或已过期");
		}
		DeviceTypeEnum requiredDevice = "mall".equals(scope) ? DeviceTypeEnum.TOC : DeviceTypeEnum.TOB;
		try {
			SecurityUtils.requireDevice(user, requiredDevice);
		}
		catch (ArynBusinessException exception) {
			throw new ArynBusinessException(403, "登录端与请求身份不匹配");
		}
		String sessionTenantId = user.getTenantId();
		String headerTenantId = request.getHeader(com.aryn.cloud.common.core.constant.CommonConstants.TENANT_ID);
		if (!StrUtil.equals(sessionTenantId, headerTenantId)) {
			throw new ArynBusinessException(403, "会话租户与请求租户不一致");
		}
		// mall 会话仅限 C 端平台；delivery 会话归属配送端（TOB 一致不等于配送资格，
		// 管理端 TOB 仍受既有 DeliveryAccessGuard 约束）
		if ("mall".equals(scope)
				&& !OpenPlatformTypeEnum.WX_MA.getCode().equals(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE))
				&& request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE) != null) {
			throw new ArynBusinessException(403, "商城会话仅支持小程序端校验");
		}
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("tenantId", sessionTenantId);
		return Result.success(payload);
	}

	/**
	 * 用户退出
	 *
	 * @author 雨滴kian
	 * @date 2022/5/3 20:46
	 * @version 1.0
	 */
	@DeleteMapping("/logout")
	public Result<Void> logout(HttpServletRequest request) {
		UserLoginReqDTO userLoginReqDTO = new UserLoginReqDTO();
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
		tocLoginService.logout(userLoginReqDTO);
		return Result.success();
	}

}
