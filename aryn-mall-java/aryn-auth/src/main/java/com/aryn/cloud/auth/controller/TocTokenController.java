
package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.aryn.cloud.auth.service.TocLoginService;
import com.aryn.cloud.common.core.constant.MallCommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

	@Operation(summary = "小程序登录")
	@PostMapping("/ma/login")
	public Result<Object> maLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
		return Result.success(tocLoginService.maLogin(userLoginReqDTO));
	}

	@Operation(summary = "小程序手机号一键登录")
	@PostMapping("/ma/phone/login")
	public Result<SaTokenInfo> maPhoneLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
		return Result.success(tocLoginService.maPhoneLogin(userLoginReqDTO));
	}

	@Operation(summary = "短信验证码登录")
	@PostMapping("/sms/login")
	public Result<SaTokenInfo> smsLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
		return Result.success(tocLoginService.smsLogin(userLoginReqDTO));
	}

	@Operation(summary = "密码登录")
	@PostMapping("/password/login")
	public Result<SaTokenInfo> passwordLogin(HttpServletRequest request, @RequestBody UserLoginReqDTO userLoginReqDTO) {
		userLoginReqDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		userLoginReqDTO.setPlatformType(request.getHeader(MallCommonConstants.HEADER_PLATFORM_TYPE));
		return Result.success(tocLoginService.passwordLogin(userLoginReqDTO));
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
