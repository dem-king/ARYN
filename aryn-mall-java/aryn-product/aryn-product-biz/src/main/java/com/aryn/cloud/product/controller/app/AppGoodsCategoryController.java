
package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.service.IGoodsCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品类目
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:30
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/goodscategory")
@Tag(description = "app-goodscategory", name = "商品类目-API")
public class AppGoodsCategoryController {

	private final IGoodsCategoryService goodsCategoryService;

	@Operation(summary = "树结构商品类目列表")
	@GetMapping("/tree")
	public Result tree() {
		return Result.success(goodsCategoryService.getGoodsCategoryTreeList());
	}

	@Operation(summary = "通过id查询商品类目")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsCategoryService.getById(id));
	}

}
