
package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.GoodsFootprint;
import com.aryn.cloud.product.service.IGoodsFootprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户足迹
 *
 * @author 雨滴kian
 * @since 2022/2/23 13:11
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/footprint")
@Tag(description = "footprint", name = "用户足迹-API")
public class AppGoodsFootprintController {

	private final IGoodsFootprintService userFootprintService;

	@Operation(summary = "用户足迹列表")
	@GetMapping("/page")
	public Result page(Page page, GoodsFootprint userFootprint) {
		userFootprint.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(userFootprintService.apiPage(page, Wrappers.lambdaQuery(userFootprint)));
	}

	@Operation(summary = "删除足迹")
	@DeleteMapping("/{id}")
	public Result del(@PathVariable String id) {
		return Result.success(userFootprintService.removeById(id));
	}

	@Operation(summary = "新增足迹")
	@PostMapping
	public Result add(@RequestBody GoodsFootprint userFootprint) {
		userFootprint.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(userFootprintService.save(userFootprint));
	}

}
