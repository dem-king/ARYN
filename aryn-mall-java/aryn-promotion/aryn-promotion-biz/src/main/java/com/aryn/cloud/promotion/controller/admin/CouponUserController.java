
package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.service.ICouponUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/couponuser")
@Tag(description = "couponuser", name = "用户领券记录")
public class CouponUserController {

	private final ICouponUserService couponUserService;

	@Operation(summary = "用户领券记录列表")
	@SaCheckPermission("promotion:couponuser:page")
	@GetMapping("/page")
	public Result page(Page page, CouponUser couponUser) {
		return Result.success(couponUserService.getPage(page, couponUser));
	}

}
