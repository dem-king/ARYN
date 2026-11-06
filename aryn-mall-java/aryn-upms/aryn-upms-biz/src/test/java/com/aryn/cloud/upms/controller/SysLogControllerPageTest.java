package com.aryn.cloud.upms.controller;

import com.aryn.cloud.upms.api.entity.SysLog;
import com.aryn.cloud.upms.service.ISysLogService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志可见性：普通角色账号的列表与详情不能包含平台管理员的记录；
 * 平台管理员会话保留完整审计。
 */
class SysLogControllerPageTest {

	@BeforeAll
	static void initLambdaCache() {
		// LambdaQueryWrapper#getSqlSegment 需要实体列缓存；纯单元测试没有 MyBatis 启动流程，这里显式初始化
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysLog.class);
	}

	private final ISysLogService sysLogService = mock(ISysLogService.class);

	private final PlatformAdminVisibilitySupport visibilitySupport = mock(PlatformAdminVisibilitySupport.class);

	private final SysLogController controller = new SysLogController(sysLogService, visibilitySupport);

	@SuppressWarnings("unchecked")
	@Test
	void pageExcludesPlatformAdminUsernamesForTenantAdmin() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of("system"));
		when(sysLogService.page(any(), any())).thenReturn(new Page<>());
		Page<SysLog> page = new Page<>(1, 10);

		controller.page(page, new SysLog());

		ArgumentCaptor<Wrapper<SysLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(sysLogService).page(eq(page), captor.capture());
		String sql = ((LambdaQueryWrapper<SysLog>) captor.getValue()).getSqlSegment();
		assertThat(sql).contains("user_name").contains("create_by").contains("NOT IN");
	}

	@SuppressWarnings("unchecked")
	@Test
	void pageKeepsFullLogForPlatformAdminSession() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of());
		when(sysLogService.page(any(), any())).thenReturn(new Page<>());
		Page<SysLog> page = new Page<>(1, 10);

		controller.page(page, new SysLog());

		ArgumentCaptor<Wrapper<SysLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(sysLogService).page(eq(page), captor.capture());
		assertThat(((LambdaQueryWrapper<SysLog>) captor.getValue()).getSqlSegment()).doesNotContain("NOT IN");
	}

	@Test
	void detailHiddenForTenantAdmin() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of("system"));
		SysLog log = new SysLog();
		log.setId("log-system");
		log.setUserName("system");
		when(sysLogService.getById("log-system")).thenReturn(log);

		var result = controller.getById("log-system");

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("日志不存在");
		assertThat(result.getData()).isNull();
	}

	@Test
	void detailOfNormalLogVisibleForTenantAdmin() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of("system"));
		SysLog log = new SysLog();
		log.setId("log-1");
		log.setUserName("admin");
		when(sysLogService.getById("log-1")).thenReturn(log);

		var result = controller.getById("log-1");

		assertThat(result.getCode()).isZero();
		assertThat(result.getData()).isSameAs(log);
	}

	@Test
	void detailOfPlatformAdminLogVisibleForPlatformAdmin() {
		when(visibilitySupport.hiddenPlatformAdminUsernames()).thenReturn(Set.of());
		SysLog log = new SysLog();
		log.setId("log-system");
		log.setUserName("system");
		when(sysLogService.getById("log-system")).thenReturn(log);

		var result = controller.getById("log-system");

		assertThat(result.getCode()).isZero();
	}

}
