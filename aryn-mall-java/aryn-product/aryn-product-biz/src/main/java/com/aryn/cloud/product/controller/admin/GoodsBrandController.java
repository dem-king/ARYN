package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.service.IGoodsBrandService;
import com.aryn.cloud.product.service.IGoodsSpuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/goodsbrand")
@Tag(description = "goodsbrand", name = "商品品牌")
public class GoodsBrandController {

	private final IGoodsBrandService goodsBrandService;

	private final IGoodsSpuService goodsSpuService;

	@Operation(summary = "商品品牌分页")
	@SaCheckPermission("product:goodsbrand:page")
	@GetMapping("/page")
	public Result page(Page<GoodsBrand> page, GoodsBrand query) {
		return Result.success(goodsBrandService.page(page, Wrappers.<GoodsBrand>lambdaQuery()
			.like(StringUtils.hasText(query.getName()), GoodsBrand::getName, query.getName())
			.eq(StringUtils.hasText(query.getStatus()), GoodsBrand::getStatus, query.getStatus())
			.orderByAsc(GoodsBrand::getSort)
			.orderByDesc(GoodsBrand::getCreateTime)));
	}

	@Operation(summary = "启用商品品牌列表")
	@SaCheckPermission("product:goodsbrand:get")
	@GetMapping("/list")
	public Result list() {
		return Result.success(goodsBrandService.lambdaQuery()
			.eq(GoodsBrand::getStatus, "0")
			.orderByAsc(GoodsBrand::getSort)
			.list());
	}

	@Operation(summary = "商品品牌详情")
	@SaCheckPermission("product:goodsbrand:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		return Result.success(goodsBrandService.getById(id));
	}

	@SysLog("新增商品品牌")
	@Operation(summary = "新增商品品牌")
	@SaCheckPermission("product:goodsbrand:add")
	@PostMapping
	public Result add(@RequestBody GoodsBrand goodsBrand) {
		return Result.success(goodsBrandService.save(goodsBrand));
	}

	@SysLog("修改商品品牌")
	@Operation(summary = "修改商品品牌")
	@SaCheckPermission("product:goodsbrand:edit")
	@PutMapping
	public Result edit(@RequestBody GoodsBrand goodsBrand) {
		return Result.success(goodsBrandService.updateById(goodsBrand));
	}

	@SysLog("删除商品品牌")
	@Operation(summary = "删除商品品牌")
	@SaCheckPermission("product:goodsbrand:del")
	@DeleteMapping("/{id}")
	public Result delete(@PathVariable String id) {
		if (goodsSpuService.lambdaQuery().eq(GoodsSpu::getBrandId, id).count() > 0) {
			return Result.fail("品牌已被商品使用，不能删除");
		}
		return Result.success(goodsBrandService.removeById(id));
	}

}
