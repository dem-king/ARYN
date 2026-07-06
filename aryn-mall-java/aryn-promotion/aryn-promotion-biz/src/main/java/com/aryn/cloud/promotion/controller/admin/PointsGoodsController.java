
package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.entity.PointsGoods;
import com.aryn.cloud.promotion.service.IPointsGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 积分商品管理端
 *
 * @author aryn
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/pointsgoods")
@Tag(description = "pointsgoods", name = "积分商品管理")
public class PointsGoodsController {

	private final IPointsGoodsService pointsGoodsService;

	@Operation(summary = "积分商品分页列表")
	@SaCheckPermission("promotion:pointsgoods:page")
	@GetMapping("/page")
	public Result page(Page page, PointsGoods pointsGoods) {
		return Result.success(pointsGoodsService.page(page));
	}

	@Operation(summary = "积分商品查询")
	@SaCheckPermission("promotion:pointsgoods:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(pointsGoodsService.getById(id));
	}

	@SysLog("新增积分商品")
	@Operation(summary = "积分商品新增")
	@SaCheckPermission("promotion:pointsgoods:add")
	@PostMapping
	public Result add(@Valid @RequestBody PointsGoods pointsGoods) {
		return Result.success(pointsGoodsService.save(pointsGoods));
	}

	@SysLog("修改积分商品")
	@Operation(summary = "积分商品修改")
	@SaCheckPermission("promotion:pointsgoods:edit")
	@PutMapping
	public Result edit(@Valid @RequestBody PointsGoods pointsGoods) {
		return Result.success(pointsGoodsService.updateById(pointsGoods));
	}

	@SysLog("删除积分商品")
	@Operation(summary = "积分商品删除")
	@SaCheckPermission("promotion:pointsgoods:del")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable("id") String id) {
		return Result.success(pointsGoodsService.removeById(id));
	}

}