package com.aryn.cloud.upms.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.vo.StatusTipConfigVO;
import com.aryn.cloud.upms.service.ISysStatusTipConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 空态插画配置（C 端免登下发）。
 *
 * <p>空态/失败态可能出现在登录前的页面（分类、搜索、活动页），故本接口免登；
 * 租户由请求头 tenant-id 携带（见 api/core/instance.ts），服务端据此取当前租户配置。
 * 返回值为空串表示该预设位未配置，由前端回落到内置静态图：
 * cloud {@code /upms/status-tip/{preset}.png}，boot {@code /boot/status-tip/{preset}.png}。</p>
 *
 * <p>免登合法性由四处共同保证：boot {@code secure.ignore.urls}、cloud 网关白名单
 * （/upms 与 /boot 双前缀）、精确 boot 兼容路由，以及
 * {@code PublicEndpointWhitelistContractTest}。</p>
 */
@AllArgsConstructor
@RestController
@RequestMapping("/app/status-tip-config")
@Tag(description = "appStatusTipConfig", name = "移动端空态插画配置")
public class AppStatusTipConfigController {

	private final ISysStatusTipConfigService statusTipConfigService;

	@Operation(summary = "获取当前租户的空态插画配置（免登）")
	@GetMapping
	public Result<StatusTipConfigVO> get() {
		return Result.success(statusTipConfigService.getRenderConfig(ArynTenantContextHolder.getTenantId()));
	}

}
