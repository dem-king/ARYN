
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.product.api.entity.Brand;
import com.aryn.cloud.product.service.IBrandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品品牌（管理端）
 *
 * @author aryn
 * @since 2026/7/5
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/brand")
@Tag(description = "brand", name = "商品品牌")
public class BrandController {

	private final IBrandService brandService;

	@Operation(summary = "品牌分页列表")
	@SaCheckPermission("product:brand:page")
	@GetMapping("/page")
	public Result page(Page page, Brand brand) {
		return Result.success(brandService.page(page, Wrappers.query(brand)));
	}

	@Operation(summary = "品牌列表")
	@SaCheckPermission("product:brand:get")
	@GetMapping("/list")
	public Result list(Brand brand) {
		return Result.success(brandService
			.list(Wrappers.query(brand).lambda().select(Brand::getId, Brand::getName)));
	}

	@Operation(summary = "通过id查询品牌")
	@SaCheckPermission("product:brand:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(brandService.getById(id));
	}

	@SysLog("新增品牌")
	@Operation(summary = "品牌新增")
	@SaCheckPermission("product:brand:add")
	@PostMapping
	public Result add(@RequestBody Brand brand) {
		return Result.success(brandService.save(brand));
	}

	@SysLog("修改品牌")
	@Operation(summary = "品牌修改")
	@SaCheckPermission("product:brand:edit")
	@PutMapping
	public Result edit(@RequestBody Brand brand) {
		return Result.success(brandService.updateById(brand));
	}

	@SysLog("删除品牌")
	@Operation(summary = "品牌删除")
	@SaCheckPermission("product:brand:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(brandService.removeById(id));
	}

}