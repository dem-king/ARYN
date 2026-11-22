package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.vo.AppLadderPriceVO;
import com.aryn.cloud.promotion.service.impl.LadderPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * C 端阶梯价档位查询（商详阶梯表 / 结算进度提示，2026-11-15 审查修复新增）。
 *
 * <p><b>免登可访问</b>（方案 §11.7 双端白名单）：阶梯价是公开价格层，
 * 按场景/船舶圈定过滤，成交仍以引擎试算为准。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/ladder")
@Tag(description = "app-ladder", name = "C端阶梯价")
public class AppLadderPriceController {

	private final LadderPriceService ladderPriceService;

	@Operation(summary = "阶梯价档位批量查询（skuIds 逗号分隔；未命中返回空列表）")
	@GetMapping("/prices")
	public Result<List<AppLadderPriceVO>> prices(
			@RequestParam("skuIds") String skuIds,
			@RequestParam(value = "purchaseScene", required = false) String purchaseScene,
			@RequestParam(value = "vesselId", required = false) String vesselId) {
		List<String> ids = Arrays.stream(skuIds.split(","))
			.map(String::trim)
			.filter(org.springframework.util.StringUtils::hasText)
			.distinct()
			.toList();
		return Result.success(ladderPriceService.ladderPrices(ArynTenantContextHolder.getTenantId(), ids,
				purchaseScene, vesselId));
	}

}