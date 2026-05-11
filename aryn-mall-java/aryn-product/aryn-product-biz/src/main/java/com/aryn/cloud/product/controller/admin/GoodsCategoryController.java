
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.service.IGoodsCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品类目
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:30
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/goodscategory")
@Tag(description = "goodscategory", name = "商品类目")
public class GoodsCategoryController {

	private final IGoodsCategoryService goodsCategoryService;

	@Operation(summary = "商品类目列表")
	@SaCheckPermission("product:goodscategory:page")
	@GetMapping("/page")
	public Result page(Page page, GoodsCategory goodsCategory) {
		return Result.success(goodsCategoryService.page(page, Wrappers.query(goodsCategory)));
	}

	@Operation(summary = "商品类目查询")
	@SaCheckPermission("product:goodscategory:get")
	@GetMapping("/list")
	public Result list(GoodsCategory goodsCategory) {
		return Result.success(goodsCategoryService
			.list(Wrappers.query(goodsCategory).lambda().select(GoodsCategory::getId, GoodsCategory::getName)));
	}

	@Operation(summary = "树结构商品类目列表")
	@SaCheckPermission("product:goodscategory:get")
	@GetMapping("/tree")
	public Result tree() {
		return Result.success(goodsCategoryService.getGoodsCategoryTreeList());
	}

	@Operation(summary = "通过id查询商品类目")
	@SaCheckPermission("product:goodscategory:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsCategoryService.getById(id));
	}

	@SysLog("新增商品类目")
	@Operation(summary = "商品类目新增")
	@SaCheckPermission("product:goodscategory:add")
	@PostMapping
	public Result add(@RequestBody GoodsCategory goodsCategory) {
		return Result.success(goodsCategoryService.save(goodsCategory));
	}

	@SysLog("修改商品类目")
	@Operation(summary = "商品类目修改")
	@SaCheckPermission("product:goodscategory:edit")
	@PutMapping
	public Result edit(@RequestBody GoodsCategory goodsCategory) {
		if (goodsCategory.getId().equals(goodsCategory.getParentId())) {
			return Result.fail("不能将本级设为父类");
		}
		return Result.success(goodsCategoryService.updateById(goodsCategory));
	}

	@SysLog("删除商品类目")
	@Operation(summary = "商品类目删除")
	@SaCheckPermission("product:goodscategory:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		long count = goodsCategoryService
			.count(Wrappers.<GoodsCategory>lambdaQuery().eq(GoodsCategory::getParentId, id));
		if (count > 0) {
			return Result.fail("存在下级类目，请先删除下级类目");
		}
		return Result.success(goodsCategoryService.removeById(id));
	}

}
