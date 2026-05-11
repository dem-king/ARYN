package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/config")
@Tag(name = "分销配置", description = "分销配置管理")
public class DistributionConfigController {

	private final IDistributionConfigService distributionConfigService;

	@Operation(summary = "分销配置分页")
	@SaCheckPermission("promotion:distributionconfig:page")
	@GetMapping("/page")
	public Result<Page<DistributionConfig>> page(Page page, DistributionConfig query) {
		return Result.success(distributionConfigService.page(page, Wrappers.query(query)));
	}

	@Operation(summary = "分销配置详情")
	@SaCheckPermission("promotion:distributionconfig:get")
	@GetMapping("/{id}")
	public Result<DistributionConfig> get(@PathVariable String id) {
		return Result.success(distributionConfigService.getById(id));
	}

	@SysLog("新增分销配置")
	@Operation(summary = "新增分销配置")
	@SaCheckPermission("promotion:distributionconfig:add")
	@PostMapping
	public Result<Boolean> add(@Valid @RequestBody DistributionConfig entity) {
		return Result.success(distributionConfigService.save(entity));
	}

	@SysLog("修改分销配置")
	@Operation(summary = "修改分销配置")
	@SaCheckPermission("promotion:distributionconfig:edit")
	@PutMapping
	public Result<Boolean> edit(@Valid @RequestBody DistributionConfig entity) {
		return Result.success(distributionConfigService.updateById(entity));
	}

	@SysLog("启用分销配置")
	@Operation(summary = "启用分销配置")
	@SaCheckPermission("promotion:distributionconfig:edit")
	@PutMapping("/enable/{id}")
	public Result<Boolean> enable(@PathVariable String id) {
		return Result.success(distributionConfigService.enableConfig(id));
	}

	@SysLog("删除分销配置")
	@Operation(summary = "删除分销配置")
	@SaCheckPermission("promotion:distributionconfig:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(distributionConfigService.removeById(id));
	}

}
