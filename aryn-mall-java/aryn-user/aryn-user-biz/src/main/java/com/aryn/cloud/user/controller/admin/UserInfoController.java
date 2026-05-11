
package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.service.IUserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/3/1 10:13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/userinfo")
@Tag(description = "userinfo", name = "商城用户")
public class UserInfoController {

	private final IUserInfoService userInfoService;

	@Operation(summary = "商城用户分页列表")
	@SaCheckPermission("user:userinfo:page")
	@GetMapping("/page")
	public Result page(Page page, UserInfo userInfo) {
		return Result.success(userInfoService.getPage(page, userInfo));
	}

	@Operation(summary = "商城用户查询")
	@GetMapping("/{id}")
	public Result page(@PathVariable("id") String id) {
		return Result.success(userInfoService.getUserById(id));
	}

	@Operation(summary = "商城用户数量查询")
	@GetMapping("/count")
	public Result count(UserInfo userInfo) {
		return Result.success(userInfoService.count(Wrappers.lambdaQuery(userInfo)));
	}

	@SysLog("新增用户")
	@Operation(summary = "用户新增")
	@SaCheckPermission("user:userinfo:add")
	@PostMapping
	public Result<Boolean> add(@RequestBody UserInfo userInfo) {
		return Result.success(userInfoService.saveUser(userInfo));
	}

	@SysLog("修改用户")
	@Operation(summary = "用户修改")
	@SaCheckPermission("user:userinfo:edit")
	@PutMapping
	public Result<Boolean> edit(@RequestBody UserInfo userInfo) {
		return Result.success(userInfoService.updateUserById(userInfo));
	}

	@Operation(summary = "商城用户统计数量查询")
	@GetMapping("/statistics")
	public Result statistics() {
		long allCount = userInfoService.count(Wrappers.lambdaQuery());
		LocalDateTime localDateTime = LocalDateTime.now();
		long todayCount = userInfoService.count(Wrappers.<UserInfo>lambdaQuery()
			.ge(UserInfo::getCreateTime, LocalDateTimeUtil.beginOfDay(localDateTime))
			.le(UserInfo::getCreateTime, LocalDateTimeUtil.endOfDay(localDateTime)));
		Map<String, Object> rt = new HashMap<>();
		// 今日数量
		rt.put("todayCount", todayCount);
		// 全部数量
		rt.put("allCount", allCount);
		return Result.success(rt);
	}

	@Operation(summary = "用户来源统计")
	@GetMapping("/source/statistics")
	public Result sourceStatistics(UserInfo userInfo) {
		return Result.success(userInfoService.sourceStatistics(userInfo));
	}

	@SysLog("删除用户")
	@Operation(summary = "删除用户")
	@SaCheckPermission("user:userinfo:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable("id") String id) {
		return Result.success(userInfoService.removeById(id));
	}

	@SysLog("调整积分")
	@Operation(summary = "调整积分")
	@SaCheckPermission("user:userinfo:edit")
	@PostMapping("/adjustPoint")
	public Result<Boolean> adjustPoint(@RequestParam String userId, @RequestParam String changeType,
			@RequestParam Integer changePoint, @RequestParam(required = false) String remark) {
		userInfoService.adjustPoint(userId, changeType, changePoint, remark);
		return Result.success(true);
	}

	@SysLog("调整余额")
	@Operation(summary = "调整余额")
	@SaCheckPermission("user:userinfo:edit")
	@PostMapping("/adjustBalance")
	public Result<Boolean> adjustBalance(@RequestParam String userId, @RequestParam String changeType,
			@RequestParam BigDecimal changeAmount, @RequestParam(required = false) String remark) {
		userInfoService.adjustBalance(userId, changeType, changeAmount, remark);
		return Result.success(true);
	}

	@SysLog("绑定社交账号")
	@Operation(summary = "绑定社交账号")
	@SaCheckPermission("user:userinfo:edit")
	@PostMapping("/bind")
	public Result<Boolean> bind(@RequestParam String userId, @RequestParam String socialAccountId,
			@RequestParam String openId) {
		userInfoService.bindSocialAccount(userId, socialAccountId, openId);
		return Result.success(true);
	}

	@SysLog("解绑社交用户")
	@Operation(summary = "解绑社交用户")
	@SaCheckPermission("user:userinfo:edit")
	@DeleteMapping("/unbind/{id}")
	public Result<Boolean> unbind(@PathVariable("id") String id) {
		userInfoService.unbindSocialUser(id);
		return Result.success(true);
	}

	@Operation(summary = "团队分页")
	@SaCheckPermission("user:userinfo:page")
	@GetMapping("/team-page")
	public Result teamPage(Page page, @RequestParam String userId) {
		return Result.success(userInfoService.getTeamPage(page, userId));
	}

}
