package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/app/goodsbrand")
@Tag(description = "app-goodsbrand", name = "商品品牌-API")
public class AppGoodsBrandController {

	private final IGoodsBrandService goodsBrandService;

	@Operation(summary = "启用商品品牌列表")
	@GetMapping("/list")
	public Result list() {
		return Result.success(goodsBrandService.list(Wrappers.<GoodsBrand>lambdaQuery()
			.select(GoodsBrand::getId, GoodsBrand::getName, GoodsBrand::getLogoUrl)
			.eq(GoodsBrand::getStatus, "0")
			.orderByAsc(GoodsBrand::getSort)));
	}

	/**
	 * C 端品牌筛选项：只返回在当前条件下**确有在售商品**的品牌。
	 *
	 * <p>与 {@link #list()} 的区别是本接口感知查询条件。分类页/搜索页必须用这个接口，
	 * 否则会出现「点了必然为空」的品牌（本租户水果等 7 个一级分类下全无品牌商品）。
	 *
	 * @param goodsSpu 查询条件（categoryFirstId / categorySecondId / name），
	 *                 与 {@code /app/goodsspu/page} 的过滤字段同名同义
	 */
	@Operation(summary = "C端品牌筛选项（按条件聚合，仅含有商品的品牌）")
	@GetMapping("/filter-list")
	public Result filterList(GoodsSpu goodsSpu) {
		return Result.success(goodsBrandService.listFilterOptions(goodsSpu));
	}

}
