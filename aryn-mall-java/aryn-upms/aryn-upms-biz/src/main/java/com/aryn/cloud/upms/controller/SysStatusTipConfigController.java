package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.dto.StatusTipConfigDTO;
import com.aryn.cloud.upms.api.vo.StatusTipConfigVO;
import com.aryn.cloud.upms.service.ISysStatusTipConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 空态插画配置（管理端）。
 *
 * <p>作用于 C 端各页面的空态/失败态插图，仅对当前租户生效。未配置的预设位由 C 端
 * 回落到随 aryn-upms 发布的内置静态图，因此这里允许整行留空。</p>
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/status-tip-config")
@Tag(description = "status-tip-config", name = "空态插画配置")
public class SysStatusTipConfigController {

	private final ISysStatusTipConfigService statusTipConfigService;

	@Operation(summary = "查询当前租户的空态插画配置")
	@SaCheckPermission("upms:statustip:get")
	@GetMapping
	public Result<StatusTipConfigVO> get() {
		return Result.success(statusTipConfigService.getConfig(ArynTenantContextHolder.getTenantId()));
	}

	@Operation(summary = "保存当前租户的空态插画配置")
	@SysLog("保存空态插画配置")
	@SaCheckPermission("upms:statustip:edit")
	@PutMapping
	public Result<Void> save(@RequestBody StatusTipConfigDTO dto) {
		statusTipConfigService.saveConfig(ArynTenantContextHolder.getTenantId(), dto);
		return Result.success();
	}

}
