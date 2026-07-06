package com.aryn.cloud.user.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.entity.MemberGrowthLog;
import com.aryn.cloud.user.api.entity.MemberLevel;
import com.aryn.cloud.user.api.entity.MemberPaidOrder;
import com.aryn.cloud.user.service.IMemberGrowthLogService;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IMemberPaidOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C端会员中心
 *
 * @author aryn
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/user/member")
@Tag(name = "会员中心-API")
public class AppMemberController {

	private final IMemberLevelService memberLevelService;

	private final IMemberPaidOrderService memberPaidOrderService;

	private final IMemberGrowthLogService memberGrowthLogService;

	@Operation(summary = "获取会员等级列表")
	@GetMapping("/levels")
	public Result<List<MemberLevel>> levelList() {
		return Result.success(memberLevelService.list(
				Wrappers.<MemberLevel>lambdaQuery().eq(MemberLevel::getStatus, "0").orderByAsc(MemberLevel::getSortOrder)));
	}

	@Operation(summary = "开通付费会员")
	@PostMapping("/paid-order")
	public Result<MemberPaidOrder> createPaidOrder(@RequestParam String memberLevelId) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(memberPaidOrderService.createOrder(userId, memberLevelId));
	}

	@Operation(summary = "我的付费会员订单")
	@GetMapping("/paid-order/page")
	public Result paidOrderPage(Page page) {
		String userId = SecurityUtils.getUser().getUserId();
		MemberPaidOrder query = new MemberPaidOrder();
		query.setUserId(userId);
		return Result.success(memberPaidOrderService.getPage(page, query));
	}

	@Operation(summary = "我的成长值记录")
	@GetMapping("/growth-log/page")
	public Result growthLogPage(Page page) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(memberGrowthLogService.getPage(page, userId));
	}

}