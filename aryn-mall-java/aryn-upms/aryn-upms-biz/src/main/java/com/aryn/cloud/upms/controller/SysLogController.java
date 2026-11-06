package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.api.entity.SysLog;
import com.aryn.cloud.upms.service.ISysLogService;
import com.aryn.cloud.upms.support.PlatformAdminVisibilitySupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 日志管理（操作日志）
 *
 * <p>平台管理员账号是系统内置账号，普通角色账号在操作日志列表/详情中不应看到它；
 * 当前会话是平台管理员时保留完整日志（审计需要）。</p>
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:39
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/syslog")
@Tag(description = "syslog", name = "日志管理")
public class SysLogController {

	private final ISysLogService sysLogService;

	private final PlatformAdminVisibilitySupport platformAdminVisibilitySupport;

	@Operation(summary = "日志列表")
	@SaCheckPermission("upms:syslog:page")
	@GetMapping("/page")
	public Result page(Page page, SysLog sysLog) {
		Set<String> hiddenUserNames = platformAdminVisibilitySupport.hiddenPlatformAdminUsernames();
		LambdaQueryWrapper<SysLog> query = Wrappers.query(sysLog).lambda();
		appendPlatformAdminFilter(query, hiddenUserNames);
		return Result.success(sysLogService.page(page, query));
	}

	@Operation(summary = "通过id查询日志")
	@SaCheckPermission("upms:syslog:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable String id) {
		SysLog sysLog = sysLogService.getById(id);
		Set<String> hiddenUserNames = platformAdminVisibilitySupport.hiddenPlatformAdminUsernames();
		// 按 ID 直查同样不可见：不知道 ID 也看不到，知道 ID 也返回“日志不存在”
		if (sysLog == null || isPlatformAdminLog(sysLog, hiddenUserNames)) {
			return Result.fail("日志不存在");
		}
		return Result.success(sysLog);
	}

	/**
	 * 普通角色账号追加「不包含平台管理员」条件；平台管理员会话 hidden 集合为空，不追加。
	 *
	 * <p>操作日志的用户名落在 {@code user_name}（异步落库时 {@code create_by} 可能为空），
	 * 两个字段都做 null-safe 过滤：显式放行 NULL，避免 {@code NOT IN} 的三值逻辑误伤存量记录。</p>
	 */
	private static void appendPlatformAdminFilter(LambdaQueryWrapper<SysLog> query, Set<String> hiddenUserNames) {
		if (hiddenUserNames == null || hiddenUserNames.isEmpty()) {
			return;
		}
		query.and(wrapper -> wrapper
			.and(name -> name.isNull(SysLog::getUserName).or().notIn(SysLog::getUserName, hiddenUserNames))
			.and(creator -> creator.isNull(SysLog::getCreateBy).or().notIn(SysLog::getCreateBy, hiddenUserNames)));
	}

	private static boolean isPlatformAdminLog(SysLog sysLog, Set<String> hiddenUserNames) {
		if (hiddenUserNames == null || hiddenUserNames.isEmpty()) {
			return false;
		}
		return (sysLog.getUserName() != null && hiddenUserNames.contains(sysLog.getUserName()))
				|| (sysLog.getCreateBy() != null && hiddenUserNames.contains(sysLog.getCreateBy()));
	}

}
