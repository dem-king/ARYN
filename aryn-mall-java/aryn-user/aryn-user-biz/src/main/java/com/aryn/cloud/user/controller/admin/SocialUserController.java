/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.service.ISocialUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三方用户
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:21:22
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/socialUser")
@Tag(description = "SocialUser", name = "三方用户")
public class SocialUserController {

	private final ISocialUserService socialUserService;

	@Operation(summary = "三方用户列表")
	@SaCheckPermission("user:socialUser:page")
	@GetMapping("/page")
	public Result<IPage<SocialUser>> page(Page page, SocialUser socialUser) {
		return Result.success(socialUserService.page(page, Wrappers.query(socialUser)));
	}

	@Operation(summary = "三方用户查询")
	@SaCheckPermission("user:socialUser:get")
	@GetMapping("/{id}")
	public Result<SocialUser> getById(@PathVariable("id") String id) {
		return Result.success(socialUserService.getById(id));
	}

	@SysLog("解绑三方用户")
	@Operation(summary = "解绑三方用户")
	@SaCheckPermission("user:socialUser:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(socialUserService.removeById(id));
	}

}
