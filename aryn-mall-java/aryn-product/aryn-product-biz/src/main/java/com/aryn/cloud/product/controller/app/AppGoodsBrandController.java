package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.entity.GoodsBrand;
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

}
