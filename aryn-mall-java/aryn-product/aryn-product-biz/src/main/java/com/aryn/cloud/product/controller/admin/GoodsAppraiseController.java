
package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @date 2022/7/30
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/appraise")
@Tag(description = "appraise", name = "商品评价")
public class GoodsAppraiseController {

	private final IGoodsAppraiseService goodsAppraiseService;

	@Operation(summary = "商品评价分页查询")
	@SaCheckPermission("product:appraise:page")
	@GetMapping("/page")
	public Result getPage(Page page, GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.getPage(page, goodsAppraise));
	}

	@Operation(summary = "通过id查询商品评价")
	@SaCheckPermission("product:appraise:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(goodsAppraiseService.getById(id));
	}

	@Operation(summary = "商品评价新增")
	@SaCheckPermission("product:appraise:add")
	@PostMapping
	public Result save(@RequestBody GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.save(goodsAppraise));
	}

	@Operation(summary = "商品评价修改")
	@SaCheckPermission("product:appraise:edit")
	@PutMapping
	public Result updateById(@RequestBody GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.updateById(goodsAppraise));
	}

	@Operation(summary = "商品评价删除")
	@SaCheckPermission("product:appraise:del")
	@DeleteMapping("/{id}")
	public Result delById(@PathVariable("id") String id) {
		return Result.success(goodsAppraiseService.removeById(id));
	}

	@Operation(summary = "回复商品评价")
	@SaCheckPermission("product:appraise:reply")
	@PostMapping("/reply")
	public Result reply(@RequestBody GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.reply(goodsAppraise));
	}

}
