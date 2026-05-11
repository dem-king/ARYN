package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.SignInConfig;
import com.aryn.cloud.user.service.ISignInConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 签到配置管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/signinconfig")
@Tag(description = "signinconfig", name = "签到配置管理")
public class SignInConfigController {

	private final ISignInConfigService signInConfigService;

	@Operation(summary = "签到配置分页列表")
	@SaCheckPermission("user:signinconfig:page")
	@GetMapping("/page")
	public Result page(Page page, SignInConfig signInConfig) {
		return Result.success(signInConfigService.getPage(page, signInConfig));
	}

	@Operation(summary = "签到配置查询")
	@SaCheckPermission("user:signinconfig:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(signInConfigService.getDetailById(id));
	}

	@SysLog("新增签到配置")
	@Operation(summary = "新增签到配置")
	@SaCheckPermission("user:signinconfig:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody SignInConfig signInConfig) {
		return Result.success(signInConfigService.saveConfig(signInConfig));
	}

	@SysLog("修改签到配置")
	@Operation(summary = "修改签到配置")
	@SaCheckPermission("user:signinconfig:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody SignInConfig signInConfig) {
		return Result.success(signInConfigService.updateConfig(signInConfig));
	}

	@SysLog("删除签到配置")
	@Operation(summary = "删除签到配置")
	@SaCheckPermission("user:signinconfig:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(signInConfigService.deleteConfig(id));
	}

}
