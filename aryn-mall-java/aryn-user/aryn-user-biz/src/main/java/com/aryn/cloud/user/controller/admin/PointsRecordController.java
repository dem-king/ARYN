package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.service.IPointsRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 积分记录查询
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/pointsrecord")
@Tag(description = "pointsrecord", name = "积分记录查询")
public class PointsRecordController {

	private final IPointsRecordService pointsRecordService;

	@Operation(summary = "积分记录分页列表")
	@SaCheckPermission("user:pointsrecord:page")
	@GetMapping("/page")
	public Result page(Page page, String nickname, String changeType, String beginTime, String endTime) {
		return Result.success(pointsRecordService.getPage(page, nickname, changeType, beginTime, endTime));
	}

	@Operation(summary = "用户积分记录分页")
	@SaCheckPermission("user:pointsrecord:page")
	@GetMapping("/user/page")
	public Result userPage(Page page, String userId) {
		return Result.success(pointsRecordService.getUserPage(page, userId));
	}

}
