package com.aryn.cloud.upms.controller;

import com.aryn.cloud.upms.api.entity.SysLoginLog;
import com.aryn.cloud.upms.service.ISysLoginLogService;
import com.aryn.cloud.upms.support.PlatformAdminVisibilitySupport;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录日志可见性：普通角色账号的列表与统计不能包含平台管理员的登录记录；
 * 平台管理员会话保留完整审计。
 */
class SysLoginLogControllerPageTest {

	@BeforeAll
	static void initLambdaCache() {
		// LambdaQueryWrapper#getSqlSegment 需要实体列缓存；纯单元测试没有 MyBatis 启动流程，这里显式初始化
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLoginLog.class);
	}

	private final ISysLoginLogService loginLogService = mock(ISysLoginLogService.class);

	private final PlatformAdminVisibilitySupport visibilitySupport = mock(PlatformAdminVisibilitySupport.class);

	private final SysLoginLogController controller = new SysLoginLogController(loginLogService, visibilitySupport);

	@SuppressWarnings("unchecked")
	@Test
	void pageExcludesPlatformAdminUsernamesForTenantAdmin() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of("system"));
		when(loginLogService.page(any(), any())).thenReturn(new Page<>());
		Page<SysLoginLog> page = new Page<>(1, 10);

		controller.page(page, new SysLoginLog());

		ArgumentCaptor<Wrapper<SysLoginLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(loginLogService).page(eq(page), captor.capture());
		String sql = ((LambdaQueryWrapper<SysLoginLog>) captor.getValue()).getSqlSegment();
		assertThat(sql).contains("user_name").contains("NOT IN");
	}

	@SuppressWarnings("unchecked")
	@Test
	void pageKeepsFullLogForPlatformAdminSession() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of());
		when(loginLogService.page(any(), any())).thenReturn(new Page<>());
		Page<SysLoginLog> page = new Page<>(1, 10);

		controller.page(page, new SysLoginLog());

		ArgumentCaptor<Wrapper<SysLoginLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(loginLogService).page(eq(page), captor.capture());
		String sql = ((LambdaQueryWrapper<SysLoginLog>) captor.getValue()).getSqlSegment();
		assertThat(sql).doesNotContain("NOT IN");
	}

	@SuppressWarnings("unchecked")
	@Test
	void statisticsApplySamePlatformAdminFilter() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of("system"));
		when(loginLogService.count(any())).thenReturn(0L);

		controller.statistics();

		ArgumentCaptor<Wrapper<SysLoginLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(loginLogService, times(2)).count(captor.capture());
		for (Wrapper<SysLoginLog> wrapper : captor.getAllValues()) {
			assertThat(((LambdaQueryWrapper<SysLoginLog>) wrapper).getSqlSegment())
				.contains("user_name")
				.contains("NOT IN");
		}
	}

}
