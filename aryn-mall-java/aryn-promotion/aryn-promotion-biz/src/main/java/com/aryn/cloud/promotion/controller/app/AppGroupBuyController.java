package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.GroupBuyJoinDTO;
import com.aryn.cloud.promotion.api.entity.GroupBuyActivity;
import com.aryn.cloud.promotion.service.IGroupBuyActivityService;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/groupbuy")
@Tag(description = "app-groupbuy", name = "拼团-API")
public class AppGroupBuyController {

	private final IGroupBuyActivityService groupBuyActivityService;

	private final IGroupBuyRecordService groupBuyRecordService;

	@Operation(summary = "拼团活动列表")
	@GetMapping("/activity/page")
	public Result activityPage(Page page, GroupBuyActivity activity) {
		return Result.success(groupBuyActivityService.getAppPage(page, activity));
	}

	@Operation(summary = "拼团活动详情")
	@GetMapping("/activity/{id}")
	public Result activityDetail(@PathVariable("id") String id) {
		return Result.success(groupBuyActivityService.getDetail(id));
	}

	@SaCheckLogin
	@Operation(summary = "开团")
	@PostMapping("/open")
	public Result openGroup(@Valid @RequestBody GroupBuyJoinDTO dto) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(groupBuyRecordService.openGroup(dto.getActivityId(), userId));
	}

	@SaCheckLogin
	@Operation(summary = "参团")
	@PostMapping("/join")
	public Result joinGroup(@Valid @RequestBody GroupBuyJoinDTO dto) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(groupBuyRecordService.joinGroup(dto.getRecordId(), userId));
	}

	@Operation(summary = "拼团记录列表")
	@GetMapping("/record/page")
	public Result recordPage(Page page, @RequestParam("activityId") String activityId) {
		String userId = StpUtil.isLogin() ? SecurityUtils.getUser().getUserId() : null;
		return Result.success(groupBuyRecordService.getPageByActivityId(page, activityId, userId));
	}
}
