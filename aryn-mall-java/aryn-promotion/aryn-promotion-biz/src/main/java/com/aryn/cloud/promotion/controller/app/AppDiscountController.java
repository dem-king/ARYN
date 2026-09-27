package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/discount")
@Tag(description = "app-discount", name = "C端折扣")
public class AppDiscountController {

	private final IDiscountActivityService discountActivityService;

	@Operation(summary = "进行中的折扣活动列表(分页，含商品明细)")
	@GetMapping("/activities")
	public Result getActiveActivities(Page page) {
		return Result.success(discountActivityService.getActiveActivityPage(page));
	}

	@SaCheckLogin
	@Operation(summary = "商品折扣信息")
	@GetMapping("/goods/{skuId}")
	public Result getGoodsDiscountInfo(@PathVariable("skuId") String skuId) {
		return Result.success(discountActivityService.getGoodsDiscountInfo(skuId));
	}
}
