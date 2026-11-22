package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.service.impl.GoodsPromotionHintsService;
import com.aryn.cloud.promotion.service.impl.GoodsPromotionHintsService.Hints;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/goods-hints")
public class AppGoodsHintsController {

	private final GoodsPromotionHintsService hintsService;

	@GetMapping("/{spuId}")
	public Result<Hints> get(@PathVariable String spuId,
			@RequestParam(required = false) String skuId, @RequestParam(required = false) String purchaseScene,
			@RequestParam(required = false) String vesselCallId) {
		var user = SecurityUtils.getUser();
		String tenantId = ArynTenantContextHolder.getTenantId();
		String userId = user != null && DeviceTypeEnum.TOC == user.getDeviceType()
				&& Objects.equals(tenantId, user.getTenantId())
			? user.getUserId() : null;
		return Result.success(hintsService.get(tenantId, userId, spuId, skuId, purchaseScene, vesselCallId));
	}
}
