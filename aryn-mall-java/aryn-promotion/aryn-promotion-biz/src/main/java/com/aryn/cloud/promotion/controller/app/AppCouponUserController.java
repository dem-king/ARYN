
package com.aryn.cloud.promotion.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.vo.CouponUserVO;
import com.aryn.cloud.promotion.service.ICouponUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/couponuser")
@Tag(description = "app-couponuser", name = "用户领券记录-API")
public class AppCouponUserController {

	private final ICouponUserService couponUserService;

	@Operation(summary = "用户领券记录列表")
	@GetMapping("/page")
	public Result<IPage<CouponUser>> page(Page page, CouponUser couponUser) {
		couponUser.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(couponUserService.getApiPage(page, couponUser));
	}

	@Operation(summary = "用户领取优惠券")
	@PostMapping
	public Result<CouponUser> page(@RequestBody CouponUser couponUser) {
		couponUser.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(couponUserService.receive(couponUser));
	}

	@Operation(summary = "查询优惠券领取数量")
	@GetMapping("/countByCouponIds")
	public Result<List<CouponUserVO>> getCountByCouponIds(String[] couponIds) {
		return Result.success(couponUserService.getCountByCouponIds(couponIds));
	}

	@Operation(summary = "查询用户可用优惠券数量")
	@GetMapping("/count")
	public Result count() {
		return Result.success(couponUserService.count(Wrappers.<CouponUser>lambdaQuery()
			.eq(CouponUser::getUserId, SecurityUtils.getUser().getUserId())
			.eq(CouponUser::getStatus, CouponUserStatusEnum.STATUS_0.getCode())));
	}

}
