package com.aryn.cloud.upms.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.vo.SysTenantShopVO;
import com.aryn.cloud.upms.service.ISysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/app/tenant")
@Tag(description = "appTenant", name = "移动端租户门店")
public class AppTenantController {

	private final ISysTenantService sysTenantService;

	@GetMapping("/shop-info")
	@Operation(summary = "获取当前租户门店公开信息")
	public Result<SysTenantShopVO> getShopInfo() {
		SysTenant tenant = sysTenantService.getById(ArynTenantContextHolder.getTenantId());
		if (tenant == null) {
			return Result.success(null);
		}
		SysTenantShopVO shop = new SysTenantShopVO();
		shop.setId(tenant.getId());
		shop.setName(tenant.getName());
		shop.setLogoUrl(tenant.getLogoUrl());
		shop.setAddress(tenant.getAddress());
		shop.setSiteUrl(tenant.getSiteUrl());
		shop.setPhone(tenant.getPhone());
		return Result.success(shop);
	}

}
