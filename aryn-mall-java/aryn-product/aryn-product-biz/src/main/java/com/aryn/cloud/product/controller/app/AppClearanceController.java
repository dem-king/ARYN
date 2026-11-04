package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.vo.AppClearanceGoodsVO;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端低价捡漏专区：按商品级出清标签选品，返回出清价与到期时间。
 *
 * <p>与会员专区接口分离（D4）：会员价依赖用户等级，出清价对所有用户一致；
 * 本接口免登录，租户由上下文注入，禁止从入参取租户。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/clearance")
@Tag(description = "app-clearance", name = "低价捡漏专区-API")
public class AppClearanceController {

	private final IGoodsZoneTagService goodsZoneTagService;

	@Operation(summary = "低价捡漏专区商品（按商品标签选品，未到期）")
	@GetMapping("/activity-goods")
	public Result<AppClearanceGoodsVO> activityGoods(
			@RequestParam(value = "limit", required = false, defaultValue = "50") long limit) {
		return Result.success(goodsZoneTagService.clearanceGoods(limit));
	}

	@Operation(summary = "批量查询有效出清价（skuIds 优先：商详/购物车；无 skuIds 时按 spuIds：商品列表卡片）")
	@GetMapping("/prices")
	public Result<AppClearancePriceVO> prices(
			@RequestParam(value = "skuIds", required = false) List<String> skuIds,
			@RequestParam(value = "spuIds", required = false) List<String> spuIds) {
		// 入参优先级（契约固定）：有精确 SKU 走原逻辑（行为完全不变）→ 只有 SPU 走列表聚合 → 都空返回空 items
		if (!CollectionUtils.isEmpty(skuIds)) {
			return Result.success(goodsZoneTagService.clearancePrices(skuIds));
		}
		if (!CollectionUtils.isEmpty(spuIds)) {
			return Result.success(goodsZoneTagService.clearanceSpuPrices(spuIds));
		}
		return Result.success(goodsZoneTagService.clearancePrices(skuIds));
	}

}
