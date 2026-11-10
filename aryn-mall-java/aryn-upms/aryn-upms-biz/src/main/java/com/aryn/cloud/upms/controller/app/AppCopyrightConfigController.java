package com.aryn.cloud.upms.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysCopyrightConfig;
import com.aryn.cloud.upms.api.vo.AppCopyrightConfigVO;
import com.aryn.cloud.upms.service.ISysCopyrightConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版权配置（免登下发）。
 *
 * <p>三端登录页都要在登录前展示品牌与版权，故本接口免登。两条取值口径不同：
 * 平台端登录页没有会话也没有 tenant-id 头，固定取平台租户配置；用户端/商户端按
 * 当前租户上下文取（C 端每个请求都带 tenant-id 头）。</p>
 *
 * <p>免登合法性由四处共同保证：boot {@code secure.ignore.urls}、cloud 网关白名单
 * （/upms 与 /boot 双前缀）、精确 boot 兼容路由，以及 {@code PublicEndpointWhitelistContractTest}。</p>
 */
@AllArgsConstructor
@RestController
@RequestMapping("/app/copyright-config")
@Tag(description = "appCopyrightConfig", name = "移动端版权配置")
public class AppCopyrightConfigController {

	private final ISysCopyrightConfigService sysCopyrightConfigService;

	@Operation(summary = "获取版权配置（登录页/页脚免登读取）")
	@GetMapping
	public Result<AppCopyrightConfigVO> get(
			@RequestParam(defaultValue = SysCopyrightConfig.CLIENT_APP) String clientType) {
		if (SysCopyrightConfig.CLIENT_PLATFORM.equals(clientType)) {
			return Result.success(sysCopyrightConfigService.getPlatformLoginConfig());
		}
		return Result.success(sysCopyrightConfigService.getRenderConfig(ArynTenantContextHolder.getTenantId(),
				clientType));
	}

}