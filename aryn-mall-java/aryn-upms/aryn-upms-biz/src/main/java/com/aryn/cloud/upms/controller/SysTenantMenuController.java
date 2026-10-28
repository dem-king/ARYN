package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.SysTenantMenuDTO;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysTenantMapper;
import com.aryn.cloud.upms.service.ISysTenantMenuService;
import com.aryn.cloud.upms.support.PlatformTenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户分配菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:37
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/tenant-menu")
@Tag(description = "tenantMenu", name = "租户分配菜单")
public class SysTenantMenuController {

	private final ISysTenantMenuService sysTenantMenuService;

	private final SysTenantMapper sysTenantMapper;

	private final SysMenuMapper sysMenuMapper;

	private final PlatformTenantGuard platformTenantGuard;

	/**
	 * 用户租户分配菜单回显
	 */
	@Operation(summary = "查询租户关联菜单列表")
	@SaCheckPermission("upms:systenant:get")
	@GetMapping("/list/{tenantId}")
	public Result get(@PathVariable("tenantId") String tenantId) {
		platformTenantGuard.requirePlatformTenant();
		requireTenant(tenantId);
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			return Result.success(sysTenantMenuService.list(Wrappers.query()));
		}
		finally {
			restoreTenant(previousTenantId);
		}
	}

	/**
	 * 租户菜单分配
	 */
	@Operation(summary = "保存租户菜单")
	@SaCheckPermission("upms:systenant:edit")
	@PostMapping("/save")
	public Result save(@RequestBody SysTenantMenuDTO sysTenantMenuDTO) {
		platformTenantGuard.requirePlatformTenant();
		if (sysTenantMenuDTO == null) {
			throw new ArynBusinessException("租户菜单不能为空");
		}
		String tenantId = sysTenantMenuDTO.getTenantId();
		requireTenant(tenantId);
		List<String> menuIds = sysTenantMenuDTO.getMenuIds() == null ? List.of()
				: sysTenantMenuDTO.getMenuIds().stream()
					.filter(StrUtil::isNotBlank)
					.map(String::trim)
					.distinct()
					.toList();
		if (!menuIds.isEmpty() && sysMenuMapper.selectBatchIds(menuIds).size() != menuIds.size()) {
			throw new ArynBusinessException("存在无效菜单，请刷新后重试");
		}
		sysTenantMenuDTO.setMenuIds(menuIds);
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			sysTenantMenuService.saveTenantMenu(sysTenantMenuDTO);
			return Result.success();
		}
		finally {
			restoreTenant(previousTenantId);
		}
	}

	private void requireTenant(String tenantId) {
		if (StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException("缺少租户标识");
		}
		SysTenant tenant = sysTenantMapper.selectById(tenantId);
		if (tenant == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
	}

	private void restoreTenant(String previousTenantId) {
		if (StrUtil.isBlank(previousTenantId)) {
			ArynTenantContextHolder.removeTenantId();
		}
		else {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

}
