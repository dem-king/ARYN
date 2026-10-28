
package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.auth.dto.DeliveryLoginRequest;
import com.aryn.cloud.auth.service.LoginService;
import com.aryn.cloud.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
	@PostMapping("/login")
	public Result<SaTokenInfo> login(String username, String password) {
		return Result.success(loginService.login(username, password));
	}

	@Operation(summary = "系统用户手机号短信登录")
	@PostMapping("/sms/login")
	public Result<SaTokenInfo> smsLogin(String phone) {
		return Result.success(loginService.smsLogin(phone));
	}

	@Operation(summary = "配送员账号登录")
	@PostMapping("/delivery-login")
	public Result<SaTokenInfo> deliveryLogin(@RequestBody @Valid DeliveryLoginRequest request) {
		return Result.success(loginService.deliveryLogin(request.getPhone(), request.getPassword()));
	}

	/**
	 * 用户退出。
	 *
	 * <p>退出必须携带有效 token（前端统一请求客户端注入 `satoken` 头）：Sa-Token 依据
	 * 请求头中的 token 撤销服务端会话；不带 token 时该方法只清理本地上下文，
	 * 「界面已退出、旧 token 仍有效」正是历史缺陷。Sa-Token 1.45 的 {@code logout()} 自身
	 * 对无 token / 已失效 token 幂等返回（不抛 NotLoginException），因此这里不吞异常：
	 * Redis 等基础设施异常必须向上抛出，由前端提示「服务端退出失败」，不能假装已退出。</p>
	 */
	@Operation(summary = "系统用户退出")
	@DeleteMapping("/logout")
	public Result<Void> logout() {
		StpUtil.logout();
		return Result.success();
	}

}
