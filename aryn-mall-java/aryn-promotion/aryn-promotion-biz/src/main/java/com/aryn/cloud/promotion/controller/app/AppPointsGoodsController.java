
package com.aryn.cloud.promotion.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.PointsGoods;
import com.aryn.cloud.promotion.service.IPointsGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * C端积分商城
 *
 * @author aryn
 */
@Slf4j
@RestController
@RequestMapping("/app/promotion/points-goods")
@Tag(description = "app-points-goods", name = "积分商城-API")
@RequiredArgsConstructor
public class AppPointsGoodsController {

	private final IPointsGoodsService pointsGoodsService;

	@Operation(summary = "积分商品列表")
	@GetMapping("/list")
	public Result list(Page page) {
		LocalDateTime now = LocalDateTime.now();
		return Result.success(pointsGoodsService.page(page,
				Wrappers.<PointsGoods>lambdaQuery()
						.le(PointsGoods::getStartTime, now)
						.ge(PointsGoods::getEndTime, now)
						.gt(PointsGoods::getStock, 0)
						.orderByDesc(PointsGoods::getCreateTime)));
	}

	@Operation(summary = "积分商品详情")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(pointsGoodsService.getById(id));
	}

	@Operation(summary = "积分兑换")
	@PostMapping("/exchange/{id}")
	public Result exchange(@PathVariable("id") String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(pointsGoodsService.exchangePointsGoods(userId, id));
	}

}
