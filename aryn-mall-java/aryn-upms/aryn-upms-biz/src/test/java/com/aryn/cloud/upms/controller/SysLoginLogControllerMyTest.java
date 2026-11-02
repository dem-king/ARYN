package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.service.ISysLoginLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 我的登录记录（自服务接口）：
 * - 只按当前账号 + 当前租户过滤；
 * - 单页上限 20，避免被当成全量日志接口；
 * - 未登录直接抛业务异常（依赖统一认证链路）。
 */
class SysLoginLogControllerMyTest {

	@Test
	void myPageScopesToCurrentUserAndClampsPageSize() {
		ISysLoginLogService loginLogService = mock(ISysLoginLogService.class);
		when(loginLogService.page(any(), any())).thenReturn(new Page<>());
		SysLoginLogController controller = new SysLoginLogController(loginLogService);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			ArynUser user = new ArynUser();
			user.setUsername("clerk");
			user.setTenantId("t1");
			security.when(SecurityUtils::requireUser).thenReturn(user);

			Page page = new Page(1, 100);
			Result result = controller.myPage(page);

			assertThat(result.getCode()).isZero();
			assertThat(page.getSize()).as("单页上限 20").isEqualTo(20);
			verify(loginLogService).page(eq(page), any());
			security.verify(SecurityUtils::requireUser);
		}
	}

	@Test
	void myPageKeepsCallerPageSizeWithinLimit() {
		ISysLoginLogService loginLogService = mock(ISysLoginLogService.class);
		when(loginLogService.page(any(), any())).thenReturn(new Page<>());
		SysLoginLogController controller = new SysLoginLogController(loginLogService);

		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			ArynUser user = new ArynUser();
			user.setUsername("clerk");
			user.setTenantId("t1");
			security.when(SecurityUtils::requireUser).thenReturn(user);

			Page page = new Page(2, 10);
			controller.myPage(page);

			assertThat(page.getSize()).isEqualTo(10);
		}
	}

	@Test
	void myPagePropagatesUnauthenticatedFailure() {
		SysLoginLogController controller = new SysLoginLogController(mock(ISysLoginLogService.class));
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(SecurityUtils::requireUser).thenThrow(new ArynBusinessException("当前会话未登录"));

			assertThatThrownBy(() -> controller.myPage(new Page(1, 10)))
				.isInstanceOf(ArynBusinessException.class);
		}
	}

}
