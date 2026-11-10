package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.dto.CopyrightConfigDTO;
import com.aryn.cloud.upms.api.entity.SysCopyrightConfig;
import com.aryn.cloud.upms.api.vo.CopyrightConfigVO;
import com.aryn.cloud.upms.service.ISysCopyrightConfigService;
import com.aryn.cloud.upms.support.PlatformTenantGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版权配置（管理端）。
 *
 * <p>权限分两层：用户端配置属租户自己的品牌，凭 {@code upms:copyright:*} 即可维护；
 * 商户端与平台端是平台级配置，走 {@code upms:copyright:platform:*}（菜单挂在平台专属
 * 应用 sys_key 下，不随套餐下发），并再经 {@link PlatformTenantGuard} 校验会话确为
 * 平台管理员（与租户管理同口径，防权限串被误授）。</p>
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/copyright-config")
@Tag(description = "copyright-config", name = "版权配置")
public class SysCopyrightConfigController {

	private final ISysCopyrightConfigService sysCopyrightConfigService;

	private final PlatformTenantGuard platformTenantGuard;

	@Operation(summary = "查询指定端的版权配置")
	@SaCheckPermission(value = { "upms:copyright:get", "upms:copyright:platform:get" }, mode = SaMode.OR)
	@GetMapping
	public Result<CopyrightConfigVO> get(@RequestParam String clientType) {
		requirePlatformScope(clientType);
		return Result.success(sysCopyrightConfigService.getConfig(ArynTenantContextHolder.getTenantId(), clientType));
	}

	@Operation(summary = "保存指定端的版权配置")
	@SysLog("保存版权配置")
	@SaCheckPermission(value = { "upms:copyright:edit", "upms:copyright:platform:edit" }, mode = SaMode.OR)
	@PutMapping
	public Result<Void> save(@RequestBody CopyrightConfigDTO dto) {
		requirePlatformScope(dto == null ? null : dto.getClientType());
		sysCopyrightConfigService.saveConfig(ArynTenantContextHolder.getTenantId(), dto);
		return Result.success();
	}

	private void requirePlatformScope(String clientType) {
		if (SysCopyrightConfig.CLIENT_MERCHANT.equals(clientType)
				|| SysCopyrightConfig.CLIENT_PLATFORM.equals(clientType)) {
			platformTenantGuard.requirePlatformTenant();
		}
	}

}