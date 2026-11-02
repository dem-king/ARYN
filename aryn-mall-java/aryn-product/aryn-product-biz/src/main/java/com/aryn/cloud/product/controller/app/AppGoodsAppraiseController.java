
package com.aryn.cloud.product.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
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

	/** 用户主动评价：C 端商详评价列表只展示主动评价 */
	private static final int APPRAISE_TYPE_USER = 0;

	/** 系统默认评价：不参与商详列表，也不参与好评率/评价数统计 */
	private static final int APPRAISE_TYPE_SYSTEM = 1;

	/** 匿名评价展示昵称 */
	private static final String ANONYMOUS_NICKNAME = "匿名用户";

	private final IGoodsAppraiseService goodsAppraiseService;

	@Operation(summary = "分页查询")
	@GetMapping("/page")
	public Result getPage(Page page, GoodsAppraise goodsAppraise) {
		// 系统默认评价不进商详评价列表（由管理员在后台查看），客户端传入的 appraiseType 一律覆盖
		goodsAppraise.setAppraiseType(APPRAISE_TYPE_USER);
		IPage<GoodsAppraise> result = goodsAppraiseService.page(page, Wrappers.query(goodsAppraise));
		result.getRecords().forEach(AppGoodsAppraiseController::maskAnonymous);
		return Result.success(result);
	}

	@Operation(summary = "分页查询用户评价")
	@GetMapping("/user-page")
	public Result getPageByUserId(Page page, GoodsAppraise goodsAppraise) {
		// 本人评价不脱敏；支持按 orderId 查询订单下各订单项的已评情况
		goodsAppraise.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(goodsAppraiseService.getPage(page, goodsAppraise));
	}

	@Operation(summary = "数量查询")
	@GetMapping("/count")
	public Result getCount(GoodsAppraise goodsAppraise) {
		return Result.success(goodsAppraiseService.getCount(goodsAppraise));
	}

	/**
	 * 匿名脱敏：匿名评价或系统默认评价不暴露真实昵称/头像/用户主键，后台仍可追溯。
	 */
	private static void maskAnonymous(GoodsAppraise goodsAppraise) {
		boolean anonymous = Integer.valueOf(1).equals(goodsAppraise.getIsAnonymous())
				|| Integer.valueOf(APPRAISE_TYPE_SYSTEM).equals(goodsAppraise.getAppraiseType());
		if (anonymous) {
			goodsAppraise.setNickname(ANONYMOUS_NICKNAME);
			goodsAppraise.setAvatarUrl(null);
			goodsAppraise.setUserId(null);
		}
	}

}
