package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.service.IRechargeConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 储值配置管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/rechargeconfig")
@Tag(description = "rechargeconfig", name = "储值配置管理")
public class RechargeConfigController {

	private final IRechargeConfigService rechargeConfigService;

	@Operation(summary = "储值配置分页列表")
	@SaCheckPermission("user:rechargeconfig:page")
	@GetMapping("/page")
	public Result page(Page page, RechargeConfig rechargeConfig) {
		return Result.success(rechargeConfigService.getPage(page, rechargeConfig));
	}

	@Operation(summary = "储值配置查询")
	@SaCheckPermission("user:rechargeconfig:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(rechargeConfigService.getDetailById(id));
	}

	@SysLog("新增储值配置")
	@Operation(summary = "新增储值配置")
	@SaCheckPermission("user:rechargeconfig:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody RechargeConfig rechargeConfig) {
		return Result.success(rechargeConfigService.saveConfig(rechargeConfig));
	}

	@SysLog("修改储值配置")
	@Operation(summary = "修改储值配置")
	@SaCheckPermission("user:rechargeconfig:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody RechargeConfig rechargeConfig) {
		return Result.success(rechargeConfigService.updateConfig(rechargeConfig));
	}

	@SysLog("删除储值配置")
	@Operation(summary = "删除储值配置")
	@SaCheckPermission("user:rechargeconfig:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(rechargeConfigService.deleteConfig(id));
	}

}
