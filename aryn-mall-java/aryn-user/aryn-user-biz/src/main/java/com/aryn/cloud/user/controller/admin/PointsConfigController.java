package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.api.entity.PointsExchange;
import com.aryn.cloud.user.api.entity.PointsExclude;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.service.IPointsConfigService;
import com.aryn.cloud.user.service.IPointsExchangeService;
import com.aryn.cloud.user.service.IPointsExcludeService;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

	private final IPointsGlobalConfigService pointsGlobalConfigService;

	private final IPointsExcludeService pointsExcludeService;

	private final IPointsExchangeService pointsExchangeService;

	@Operation(summary = "积分全局规则（有效期/抵扣上限/门槛/封顶）")
	@SaCheckPermission("user:pointsconfig:page")
	@GetMapping("/global")
	public Result<PointsGlobalConfig> getGlobalConfig() {
		return Result.success(pointsGlobalConfigService.getEffectiveConfig());
	}

	@SysLog("保存积分全局规则")
	@Operation(summary = "保存积分全局规则（每租户一行）")
	@SaCheckPermission("user:pointsconfig:edit")
	@PutMapping("/global")
	public Result<Boolean> saveGlobalConfig(@RequestBody PointsGlobalConfig config) {
		return Result.success(pointsGlobalConfigService.saveConfig(config));
	}

	@Operation(summary = "积分排除清单（品类/商品黑名单）")
	@SaCheckPermission("user:pointsconfig:page")
	@GetMapping("/exclude/list")
	public Result<List<PointsExclude>> listExclude() {
		return Result.success(pointsExcludeService.listAll());
	}

	@SysLog("新增积分排除规则")
	@Operation(summary = "新增积分排除规则")
	@SaCheckPermission("user:pointsconfig:add")
	@PostMapping("/exclude")
	public Result<Boolean> addExclude(@RequestBody PointsExclude exclude) {
		return Result.success(pointsExcludeService.addExclude(exclude));
	}

	@SysLog("移除积分排除规则")
	@Operation(summary = "移除积分排除规则")
	@SaCheckPermission("user:pointsconfig:del")
	@DeleteMapping("/exclude/{id}")
	public Result<Boolean> removeExclude(@PathVariable("id") String id) {
		return Result.success(pointsExcludeService.removeExclude(id));
	}

	@Operation(summary = "积分兑换项列表（含下架）")
	@SaCheckPermission("user:pointsconfig:page")
	@GetMapping("/exchange/list")
	public Result<List<PointsExchange>> listExchange() {
		return Result.success(pointsExchangeService.listAll());
	}

	@SysLog("保存积分兑换项")
	@Operation(summary = "保存积分兑换项（新增/编辑）")
	@SaCheckPermission("user:pointsconfig:edit")
	@PutMapping("/exchange")
	public Result<Boolean> saveExchange(@RequestBody PointsExchange exchange) {
		return Result.success(pointsExchangeService.saveExchange(exchange));
	}

	@SysLog("删除积分兑换项")
	@Operation(summary = "删除积分兑换项")
	@SaCheckPermission("user:pointsconfig:del")
	@DeleteMapping("/exchange/{id}")
	public Result<Boolean> removeExchange(@PathVariable("id") String id) {
		return Result.success(pointsExchangeService.removeExchange(id));
	}

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
