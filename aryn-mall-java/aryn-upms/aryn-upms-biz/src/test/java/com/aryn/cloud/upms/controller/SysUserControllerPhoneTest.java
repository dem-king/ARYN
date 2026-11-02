package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.vo.SysUserVO;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 员工手机号可见性契约：
 * <ol>
 *   <li>列表/详情响应中的手机号仍按 VO 脱敏；</li>
 *   <li>完整号码只能通过独立接口获取，且该接口由 {@code upms:sysuser:phone} 权限与操作日志保护。</li>
 * </ol>
 */
class SysUserControllerPhoneTest {

	private static final String FULL_PHONE = "17615123399";

	private static final String MASKED_PHONE = "176****3399";

	private final ISysUserService userService = mock(ISysUserService.class);

	private final SysUserController controller = new SysUserController(userService,
			mock(ISysRoleService.class), mock(ISysUserRoleService.class),
			mock(SysUserSessionRevoker.class), mock(RemoteDeliveryAccountService.class));

	private SysUser userWithPhone(String phone) {
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setUsername("admin");
		user.setPhone(phone);
		return user;
	}

	@Test
	void phoneReturnsFullNumberForAuthorizedView() {
		when(userService.getById("user-1")).thenReturn(userWithPhone(FULL_PHONE));

		Result<String> result = controller.phone("user-1");

		assertThat(result.getCode()).isZero();
		assertThat(result.getData()).isEqualTo(FULL_PHONE);
	}

	@Test
	void phoneFailsWhenUserMissing() {
		Result<String> result = controller.phone("ghost");

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("用户不存在");
		assertThat(result.getData()).isNull();
	}

	@Test
	void phoneFailsWhenAccountHasNoPhone() {
		when(userService.getById("user-1")).thenReturn(userWithPhone(null));

		Result<String> result = controller.phone("user-1");

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).isEqualTo("该账号未绑定手机号");
	}

	@Test
	void listAndDetailVoStillMaskPhone() throws Exception {
		String json = new ObjectMapper().writeValueAsString(SysUserVO.from(userWithPhone(FULL_PHONE)));

		assertThat(json).contains(MASKED_PHONE);
		assertThat(json).doesNotContain(FULL_PHONE);
	}

}
