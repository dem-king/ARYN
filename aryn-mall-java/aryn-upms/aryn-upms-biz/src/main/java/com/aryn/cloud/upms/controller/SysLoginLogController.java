
package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.entity.SysLoginLog;
import com.aryn.cloud.upms.service.ISysLoginLogService;
import com.aryn.cloud.upms.support.PlatformAdminVisibilitySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 登录日志管理
 *
 * <p>平台管理员账号是系统内置账号，普通角色账号在登录日志列表与统计中不应看到它；
 * 当前会话是平台管理员时保留完整日志（审计需要）。</p>
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:40
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/sysloginlog")
@Tag(description = "sysloginlog", name = "登录日志")
public class SysLoginLogController {

	private final ISysLoginLogService sysLoginLogService;

	private final PlatformAdminVisibilitySupport platformAdminVisibilitySupport;

	@Operation(summary = "登录日志列表")
	@SaCheckPermission("upms:sysloginlog:page")
	@GetMapping("/page")
	public Result page(Page page, SysLoginLog sysLoginLog) {
		Set<String> hiddenUserNames = platformAdminVisibilitySupport.hiddenPlatformAdminUsernames();
		LambdaQueryWrapper<SysLoginLog> query = Wrappers.query(sysLoginLog).lambda();
		appendPlatformAdminFilter(query, hiddenUserNames);
		return Result.success(sysLoginLogService.page(page, query));
	}

	/**
	 * 我的登录记录（当前登录账号，自服务口径）。
	 *
	 * <p>不需要 {@code upms:sysloginlog:page} 权限：只按当前账号 + 当前租户过滤，
	 * 普通员工/管理员都能查看自己的最近登录记录（登录设备页使用）。
	 * 单页上限 20 条，避免被当成全量日志接口。</p>
	 */
	@Operation(summary = "我的登录记录")
	@GetMapping("/my")
	public Result myPage(Page page) {
		ArynUser user = SecurityUtils.requireUser();
		if (page.getSize() <= 0 || page.getSize() > 20) {
			page.setSize(20);
		}
		return Result.success(sysLoginLogService.page(page, Wrappers.<SysLoginLog>lambdaQuery()
			.eq(SysLoginLog::getUserName, user.getUsername())
			.eq(SysLoginLog::getTenantId, user.getTenantId())
			.orderByDesc(SysLoginLog::getCreateTime)));
	}

	@Operation(summary = "统计查询")
	@GetMapping("/statistics")
	public Result statistics() {
		// 统计口径与列表一致：普通角色账号看到的数量不能包含平台管理员登录记录
		Set<String> hiddenUserNames = platformAdminVisibilitySupport.hiddenPlatformAdminUsernames();
		// 全部
		LambdaQueryWrapper<SysLoginLog> allQuery = Wrappers.lambdaQuery();
		appendPlatformAdminFilter(allQuery, hiddenUserNames);
		long allCount = sysLoginLogService.count(allQuery);
		// 今日
		LocalDateTime localDateTime = LocalDateTime.now();
		LambdaQueryWrapper<SysLoginLog> todayQuery = Wrappers.lambdaQuery();
		appendPlatformAdminFilter(todayQuery, hiddenUserNames);
		long todayCount = sysLoginLogService.count(todayQuery
			.ge(SysLoginLog::getCreateTime, LocalDateTimeUtil.beginOfDay(localDateTime))
			.le(SysLoginLog::getCreateTime, LocalDateTimeUtil.endOfDay(localDateTime)));
		Map<String, Object> rt = new HashMap<>();
		// 今日数量
		rt.put("todayCount", todayCount);
		// 全部数量
		rt.put("allCount", allCount);
		return Result.success(rt);
	}

	/**
	 * 普通角色账号追加「不包含平台管理员」条件；平台管理员会话 hidden 集合为空，不追加。
	 *
	 * <p>{@code NOT IN} 对 NULL 不成立，这里显式放行 {@code user_name IS NULL} 的历史记录，
	 * 保证过滤只隐藏平台管理员、不误伤存量脏数据。</p>
	 */
	private static void appendPlatformAdminFilter(LambdaQueryWrapper<SysLoginLog> query,
			Set<String> hiddenUserNames) {
		if (hiddenUserNames == null || hiddenUserNames.isEmpty()) {
			return;
		}
		query.and(wrapper -> wrapper.isNull(SysLoginLog::getUserName)
			.or()
			.notIn(SysLoginLog::getUserName, hiddenUserNames));
	}

}
