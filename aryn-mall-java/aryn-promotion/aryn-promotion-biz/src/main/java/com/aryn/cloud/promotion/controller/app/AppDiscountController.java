package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.vo.AppDiscountVO;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

	/**
	 * 批量查询指定 SKU 的当前最优折扣价（商详 / 购物车 / 加购确认弹层展示用）。
	 *
	 * <p>免登可访问（与折扣会场列表同一条白名单语义）：折扣价是活动配置的公开价格，
	 * 不随用户变化，未登录也要能看到会场同款折后价，否则会出现「会场标折后价、
	 * 商详/购物车标原价」的前后不一致。响应不含任何用户维度数据，
	 * 单次请求按逗号分隔的 skuIds 批量返回，禁止前端逐行请求。
	 *
	 * <p>为什么单 SKU 接口 {@code /goods/{skuId}} 仍保留 {@code @SaCheckLogin}：
	 * 它服务于「商品最优折扣」单点场景，属既有语义，本次不改；批量接口是新增的免登读接口。
	 */
	@Operation(summary = "指定SKU的折扣价（免登，批量；未命中折扣的SKU不返回）")
	@GetMapping("/prices")
	public Result<List<AppDiscountVO>> getGoodsDiscountPrices(@RequestParam("skuIds") List<String> skuIds) {
		return Result.success(discountActivityService.getGoodsDiscountPrices(skuIds));
	}
}
