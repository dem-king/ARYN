package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.service.IPointsConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 积分配置管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/pointsconfig")
@Tag(description = "pointsconfig", name = "积分配置管理")
public class PointsConfigController {

	private final IPointsConfigService pointsConfigService;

	@Operation(summary = "积分配置分页列表")
	@SaCheckPermission("user:pointsconfig:page")
	@GetMapping("/page")
	public Result page(Page page, PointsConfig pointsConfig) {
		return Result.success(pointsConfigService.getPage(page, pointsConfig));
	}

	@Operation(summary = "积分配置查询")
	@SaCheckPermission("user:pointsconfig:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(pointsConfigService.getDetailById(id));
	}

	@SysLog("新增积分配置")
	@Operation(summary = "新增积分配置")
	@SaCheckPermission("user:pointsconfig:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody PointsConfig pointsConfig) {
		return Result.success(pointsConfigService.saveConfig(pointsConfig));
	}

	@SysLog("修改积分配置")
	@Operation(summary = "修改积分配置")
	@SaCheckPermission("user:pointsconfig:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody PointsConfig pointsConfig) {
		return Result.success(pointsConfigService.updateConfig(pointsConfig));
	}

	@SysLog("删除积分配置")
	@Operation(summary = "删除积分配置")
	@SaCheckPermission("user:pointsconfig:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(pointsConfigService.deleteConfig(id));
	}

}
