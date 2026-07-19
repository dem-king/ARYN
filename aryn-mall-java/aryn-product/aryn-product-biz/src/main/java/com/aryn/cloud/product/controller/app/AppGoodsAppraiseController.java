
package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
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
@RequestMapping("/app/goodsappraise")
@Tag(description = "app-goodsappraise", name = "商品评价-API")
public class AppGoodsAppraiseController {

	private final IGoodsAppraiseService goodsAppraiseService;

	@Operation(summary = "分页查询")
	@GetMapping("/page")
	public Result getPage(Page page, GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.page(page, Wrappers.query(goodsAppraise)));
	}

	@Operation(summary = "分页查询用户评价")
	@GetMapping("/user-page")
	public Result getPageByUserId(Page page, GoodsAppraise goodsAppraise) {
		goodsAppraise.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(goodsAppraiseService.page(page, Wrappers.query(goodsAppraise)));
	}

	@Operation(summary = "数量查询")
	@GetMapping("/count")
	public Result getCount(GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.getCount(goodsAppraise));
	}

}
