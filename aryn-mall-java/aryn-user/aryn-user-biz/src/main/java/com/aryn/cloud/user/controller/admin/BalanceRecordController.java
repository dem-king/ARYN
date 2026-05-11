package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.service.IBalanceRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 余额变动记录查询
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/balancerecord")
@Tag(description = "balancerecord", name = "余额变动记录查询")
public class BalanceRecordController {

	private final IBalanceRecordService balanceRecordService;

	@Operation(summary = "余额记录分页列表")
	@SaCheckPermission("user:balancerecord:page")
	@GetMapping("/page")
	public Result page(Page page, String nickname, String changeType, String beginTime, String endTime) {
		return Result.success(balanceRecordService.getPage(page, nickname, changeType, beginTime, endTime));
	}

	@Operation(summary = "用户余额记录分页")
	@SaCheckPermission("user:balancerecord:page")
	@GetMapping("/user/page")
	public Result userPage(Page page, String userId) {
		return Result.success(balanceRecordService.getUserPage(page, userId));
	}

}
