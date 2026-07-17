package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionUserRegisterDTO;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/user")
@Tag(name = "分销用户", description = "分销用户管理")
public class DistributionUserController {

	private final IDistributionUserService distributionUserService;

	@Operation(summary = "分销用户分页")
	@SaCheckPermission("promotion:distributionuser:page")
	@GetMapping("/page")
	public Result<Page<DistributionUser>> page(Page page, DistributionUser query) {
		return Result.success(distributionUserService.page(page, Wrappers.query(query)));
	}

	@Operation(summary = "分销用户详情")
	@SaCheckPermission("promotion:distributionuser:get")
	@GetMapping("/{id}")
	public Result<DistributionUser> get(@PathVariable String id) {
		return Result.success(distributionUserService.getById(id));
	}

	@SysLog("注册分销用户")
	@Operation(summary = "注册分销用户")
	@SaCheckPermission("promotion:distributionuser:add")
	@PostMapping("/register")
	public Result<DistributionUser> register(@Valid @RequestBody DistributionUserRegisterDTO dto) {
		return Result.success(distributionUserService.register(dto));
	}

	@SysLog("启用分销用户")
	@Operation(summary = "启用分销用户")
	@SaCheckPermission("promotion:distributionuser:edit")
	@PutMapping("/enable/{userId}")
	public Result<Boolean> enable(@PathVariable String userId) {
		return Result.success(distributionUserService.updateStatus(userId, MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE));
	}

	@SysLog("禁用分销用户")
	@Operation(summary = "禁用分销用户")
	@SaCheckPermission("promotion:distributionuser:edit")
	@PutMapping("/disable/{userId}")
	public Result<Boolean> disable(@PathVariable String userId) {
		return Result.success(distributionUserService.updateStatus(userId, MallEventConstants.DISTRIBUTION_USER_STATUS_DISABLE));
	}

	@SysLog("删除分销用户")
	@Operation(summary = "删除分销用户")
	@SaCheckPermission("promotion:distributionuser:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(distributionUserService.removeSafely(id));
	}

}
