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
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.vo.SocialAccountVO;
import com.aryn.cloud.user.service.ISocialAccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 三方账号
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:20:56
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/socialAccount")
@Tag(description = "socialAccount", name = "三方账号")
public class SocialAccountController {

	private final ISocialAccountService socialAccountService;

	@Operation(summary = "三方账号列表")
	@SaCheckPermission("user:socialAccount:page")
	@GetMapping("/page")
	public Result<IPage<SocialAccountVO>> page(Page page, SocialAccount socialAccount) {
		return Result.success(socialAccountService.getPage(page, socialAccount));
	}

	@Operation(summary = "通过id查询三方账号")
	@SaCheckPermission("user:socialAccount:get")
	@GetMapping("/{id}")
	public Result<SocialAccountVO> getById(@PathVariable String id) {
		return Result.success(socialAccountService.getSocialAccountById(id));
	}

	@SysLog("新增三方账号")
	@Operation(summary = "三方账号新增")
	@SaCheckPermission("user:socialAccount:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SocialAccount socialAccount) {
		return Result.success(socialAccountService.saveSocialAccount(socialAccount));
	}

	@SysLog("修改三方账号")
	@Operation(summary = "三方账号修改")
	@SaCheckPermission("user:socialAccount:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody @Valid SocialAccount socialAccount) {
		return Result.success(socialAccountService.updateSocialAccountById(socialAccount));
	}

	@SysLog("删除三方账号")
	@Operation(summary = "三方账号删除")
	@SaCheckPermission("user:socialAccount:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(socialAccountService.removeSocialAccountById(id));
	}

}
