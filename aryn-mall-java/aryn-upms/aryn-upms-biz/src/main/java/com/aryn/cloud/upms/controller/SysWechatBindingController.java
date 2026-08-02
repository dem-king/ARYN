package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.service.ISysUserWechatBindingService;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 员工微信绑定
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/wechat/binding")
@Tag(description = "app-wechat-binding", name = "员工微信绑定-API")
public class SysWechatBindingController {

	private final ISysUserWechatBindingService wechatBindingService;

	@DubboReference
	private RemoteSocialUserService remoteSocialUserService;

	@Operation(summary = "通过微信 login code 绑定")
	@PostMapping("/bind-by-code")
	public Result<Boolean> bindByCode(@RequestParam String appId, @RequestParam String code) {
		UserLoginReqDTO loginReq = new UserLoginReqDTO();
		loginReq.setAppId(appId);
		loginReq.setJsCode(code);
		SocialUser socialUser = remoteSocialUserService.socialLogin(loginReq);
		if (socialUser == null || socialUser.getOpenId() == null) {
			return Result.fail("获取openid失败");
		}
		String userId = SecurityUtils.getUserId();
		String tenantId = SecurityUtils.getTenantId();
		wechatBindingService.bindOrUpdate(tenantId, userId, appId, socialUser.getOpenId());
		return Result.success(true);
	}

	@Operation(summary = "绑定微信 openid")
	@PostMapping("/bind")
	public Result<Boolean> bind(@RequestParam String appId, @RequestParam String openid) {
		String userId = SecurityUtils.getUserId();
		String tenantId = SecurityUtils.getTenantId();
		wechatBindingService.bindOrUpdate(tenantId, userId, appId, openid);
		return Result.success(true);
	}

	@Operation(summary = "查询当前用户绑定状态")
	@PostMapping("/status")
	public Result<Boolean> status() {
		String userId = SecurityUtils.getUserId();
		String tenantId = SecurityUtils.getTenantId();
		return Result.success(wechatBindingService.getActiveBinding(tenantId, userId) != null);
	}
}
