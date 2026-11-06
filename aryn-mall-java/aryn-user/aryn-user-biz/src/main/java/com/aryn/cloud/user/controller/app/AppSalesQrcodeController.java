package com.aryn.cloud.user.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.vo.SalesQrcodeVO;
import com.aryn.cloud.user.service.ISalesQrcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售个人推广小程序码（user 域，只有 user 域持有 WxMaService）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/sales")
@Tag(name = "销售推广码-API", description = "销售个人推广小程序码")
public class AppSalesQrcodeController {

	private final ISalesQrcodeService salesQrcodeService;

	@Operation(summary = "获取当前销售的个人推广小程序码")
	@GetMapping("/qrcode")
	public Result<SalesQrcodeVO> qrcode() {
		StpUtil.isLogin();
		String memberUserId = SecurityUtils.getUser().getUserId();
		String tenantId = ArynTenantContextHolder.getTenantId();
		return Result.success(salesQrcodeService.getQrcode(tenantId, memberUserId));
	}

}
