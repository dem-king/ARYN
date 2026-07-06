
package com.aryn.cloud.product.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.service.IBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品品牌（C端）
 *
 * @author aryn
 * @since 2026/7/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/brand")
@Tag(description = "app-brand", name = "商品品牌-API")
public class AppBrandController {

	private final IBrandService brandService;

	@Operation(summary = "品牌列表")
	@GetMapping("/list")
	public Result list() {
		return Result.success(brandService.listAppBrands());
	}

	@Operation(summary = "通过id查询品牌")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(brandService.getById(id));
	}

}