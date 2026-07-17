
package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品spu
 *
 * @author 雨滴kian
 * @since 2022/3/1 10:13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/goodsspu")
@Tag(description = "app-goodsspu", name = "商品spu-API")
public class AppGoodsSpuController {

	private final IGoodsSpuService goodsSpuService;

	@Operation(summary = "商品列表")
	@GetMapping("/page")
	public Result page(Page page, GoodsSpu goodsSpu) {
		return Result.success(goodsSpuService.apiPage(page, goodsSpu));
	}

	@Operation(summary = "通过id查询商品")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsSpuService.getApiSpuById(id));
	}

	@Operation(summary = "通过ids查询商品")
	@GetMapping("/list/{ids}")
	public Result<List<GoodsSpu>> getById(@PathVariable List<String> ids) {
		return Result.success(goodsSpuService
			.list(Wrappers.<GoodsSpu>lambdaQuery().in(GoodsSpu::getId, ids).eq(GoodsSpu::getStatus, "1")));
	}

	@Operation(summary = "获取热搜商品 Top10")
	@GetMapping("/hot-search/top10")
	public Result<List<GoodsSpu>> getTop10HotSearchGoods() {
		return Result.success(goodsSpuService.getTop10HotSearchGoods());
	}

}
