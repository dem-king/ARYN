
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.RegisterTenantDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.service.ISysTenantService;
import com.aryn.cloud.upms.service.TenantOverviewService;
import com.aryn.cloud.upms.support.PlatformTenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 租户管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/tenant")
@Tag(description = "tenant", name = "租户管理")
public class SysTenantController {

	private final ISysTenantService sysTenantService;

	private final TenantOverviewService tenantOverviewService;

	private final PlatformTenantGuard platformTenantGuard;

	@Operation(summary = "租户列表")
	@SaCheckPermission("upms:systenant:page")
	@GetMapping("/page")
	public Result page(Page page, SysTenant sysTenant,
			@RequestParam(required = false) Integer expiringDays) {
		platformTenantGuard.requirePlatformTenant();
		LambdaQueryWrapper<SysTenant> wrapper = Wrappers.lambdaQuery(sysTenant);
		if (expiringDays != null && expiringDays > 0) {
			LocalDateTime now = LocalDateTime.now();
			wrapper.ge(SysTenant::getAuthEndTime, now)
				.le(SysTenant::getAuthEndTime, now.plusDays(expiringDays));
		}
		return Result.success(sysTenantService.page(page, wrapper));
	}

	@Operation(summary = "租户查询")
	@SaCheckPermission("upms:systenant:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.getById(id));
	}

	@Operation(summary = "租户概览")
	@SaCheckPermission("upms:systenant:get")
	@GetMapping("/{id}/overview")
	public Result overview(@PathVariable String id) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(tenantOverviewService.getOverview(id));
	}

	@Operation(summary = "租户新增")
	@SysLog("新增租户")
	@SaCheckPermission("upms:systenant:add")
	@PostMapping
	public Result add(@RequestBody SysTenant sysTenant) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.saveTenant(sysTenant));
	}

	@Operation(summary = "租户修改")
	@SysLog("修改租户")
	@SaCheckPermission("upms:systenant:edit")
	@PutMapping
	public Result edit(@RequestBody SysTenant sysTenant) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.updateTenant(sysTenant));
	}

	@Operation(summary = "租户删除")
	@SysLog("删除租户")
	@SaCheckPermission("upms:systenant:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.removeTenant(id));
	}

	@Operation(summary = "启用/停用租户")
	@SysLog("启用停用租户")
	@SaCheckPermission("upms:systenant:edit")
	@PutMapping("/{id}/status")
	public Result changeStatus(@PathVariable String id, @RequestParam String status) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.changeStatus(id, status));
	}

	@Operation(summary = "租户续期")
	@SysLog("租户续期")
	@SaCheckPermission("upms:systenant:edit")
	@PutMapping("/{id}/renew")
	public Result renew(@PathVariable String id, @RequestParam Integer days) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.renew(id, days));
	}

	@Operation(summary = "强制下线租户会话")
	@SysLog("强制下线租户会话")
	@SaCheckPermission("upms:systenant:edit")
	@DeleteMapping("/{id}/sessions")
	public Result kickoutSessions(@PathVariable String id) {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService.kickoutSessions(id));
	}

	@Operation(summary = "记录平台切换租户")
	@SysLog("切换租户")
	@SaCheckPermission("upms:systenant:get")
	@PostMapping("/{id}/switch-audit")
	public Result switchAudit(@PathVariable String id) {
		platformTenantGuard.requirePlatformTenant();
		if (sysTenantService.getById(id) == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
		return Result.success();
	}

	@Operation(summary = "注册（创建租户）")
	@PostMapping("/register")
	public Result register(@RequestBody RegisterTenantDTO registerTenantDTO) {
		return Result.success(sysTenantService.register(registerTenantDTO));

	}

	@GetMapping("/list")
	@SaCheckPermission("upms:systenant:page")
	public Result getList() {
		platformTenantGuard.requirePlatformTenant();
		return Result.success(sysTenantService
			.list(Wrappers.<SysTenant>lambdaQuery().eq(SysTenant::getStatus, CommonConstants.NORMAL_STATUS)));
	}

}
