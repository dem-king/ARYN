
package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.auth.service.LoginService;
import com.aryn.cloud.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权登录
 *
 * @author 雨滴kian
 * @since 2022/2/10 16:25
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/token")
@Tag(description = "token", name = "授权登录")
public class TobTokenController {

	private final LoginService loginService;

	@Operation(summary = "系统用户账号登录")
	@RequestMapping("/login")
	public Result<SaTokenInfo> login(String username, String password) {
		return Result.success(loginService.login(username, password));
	}

	@Operation(summary = "系统用户手机号短信登录")
	@RequestMapping("/sms/login")
	public Result<SaTokenInfo> smsLogin(String phone) {
		return Result.success(loginService.smsLogin(phone));
	}

	/**
	 * 用户退出
	 *
	 * @author 雨滴kian
	 * @date 2022/5/3 20:46
	 * @version 1.0
	 */
	@Operation(summary = "系统用户退出")
	@DeleteMapping("/logout")
	public Result<Void> logout() {
		StpUtil.logout();
		return Result.success();
	}

}
