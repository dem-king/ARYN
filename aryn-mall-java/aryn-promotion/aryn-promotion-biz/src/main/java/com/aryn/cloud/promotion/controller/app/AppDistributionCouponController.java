package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.DistributionCouponExchangeDTO;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.vo.DistributionCouponPackVO;
import com.aryn.cloud.promotion.service.IDistributionCouponExchangeService;
import com.aryn.cloud.promotion.service.IDistributionCouponPackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分销中心-佣金兑换优惠券（C 端）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/distribution/coupon")
@Tag(name = "分销券兑换-API", description = "移动端佣金兑换优惠券")
public class AppDistributionCouponController {

	private final IDistributionCouponPackService distributionCouponPackService;

	private final IDistributionCouponExchangeService distributionCouponExchangeService;

	@Operation(summary = "可兑换券包列表")
	@GetMapping("/pack/list")
	public Result<List<DistributionCouponPackVO>> packList() {
		StpUtil.isLogin();
		return Result.success(distributionCouponPackService.listOnShelf());
	}

	@Operation(summary = "佣金兑换优惠券")
	@PostMapping("/exchange")
	public Result<Boolean> exchange(@Valid @RequestBody DistributionCouponExchangeDTO dto) {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionCouponExchangeService.exchange(userId, dto));
	}

	@Operation(summary = "我的兑换记录")
	@GetMapping("/exchange/page")
	public Result<IPage<DistributionCouponExchange>> exchangePage(Page<DistributionCouponExchange> page) {
		StpUtil.isLogin();
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(distributionCouponExchangeService.getUserPage(page, userId));
	}

}
