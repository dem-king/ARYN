package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.dto.SysUserDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.vo.SysUserVO;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工账号安全契约：
 * <ol>
 *   <li>改密保留旧密码校验、成功后撤销全部会话；</li>
 *   <li>新增/改密执行服务端密码强度校验（弱密码不落库）；</li>
 *   <li>用户信息接口不返回 BCrypt 密码 hash。</li>
 * </ol>
 */
class SysUserControllerPasswordTest {

	private static final String OLD_PASSWORD = "OldPass123";

	private static final String STRONG_NEW_PASSWORD = "NewPass2026";

	private final ISysUserService userService = mock(ISysUserService.class);

	private final ISysRoleService sysRoleService = mock(ISysRoleService.class);

	private final ISysUserRoleService sysUserRoleService = mock(ISysUserRoleService.class);

	private final SysUserSessionRevoker revoker = mock(SysUserSessionRevoker.class);

	private final SysUserController controller = new SysUserController(userService, sysRoleService,
			sysUserRoleService, revoker, mock(RemoteDeliveryAccountService.class));

	private SysUser existingUser() {
		SysUser existing = new SysUser();
		existing.setId("user-1");
		existing.setUsername("admin");
		existing.setNickname("管理员");
		existing.setPassword(BCrypt.hashpw(OLD_PASSWORD));
		return existing;
	}

	private SysUserDTO changePasswordRequest(String newPassword) {
		SysUserDTO request = new SysUserDTO();
		request.setId("user-1");
		request.setPassword(OLD_PASSWORD);
		request.setNewPassword(newPassword);
		request.setCheckPassword(newPassword);
		return request;
	}

	@Test
	void editPasswordVerifiesOldPasswordAndStoresNewPasswordHash() {
		SysUser existing = existingUser();
		when(userService.getById("user-1")).thenReturn(existing);
		when(userService.updateById(existing)).thenReturn(true);

		Result result = controller.editPwd(changePasswordRequest(STRONG_NEW_PASSWORD));

		assertThat(result.getCode()).isZero();
		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(BCrypt.checkpw(STRONG_NEW_PASSWORD, captor.getValue().getPassword())).isTrue();
		assertThat(BCrypt.checkpw(OLD_PASSWORD, captor.getValue().getPassword())).isFalse();
		// 改密即失效：删除该账号所有已登录会话
		verify(revoker).revokeAll("user-1", SessionInvalidSubReason.PASSWORD_CHANGED);
	}

	@Test
	void editPasswordDoesNotRevokeSessionsWhenPasswordUpdateFails() {
		SysUser existing = existingUser();
		when(userService.getById("user-1")).thenReturn(existing);
		when(userService.updateById(existing)).thenReturn(false);

		controller.editPwd(changePasswordRequest(STRONG_NEW_PASSWORD));

		// 密码没有实际写库时不能把用户踢下线
		verify(revoker, never()).revokeAll("user-1", SessionInvalidSubReason.PASSWORD_CHANGED);
	}

	@Test
	void editPasswordRejectsWeakNewPassword() {
		SysUser existing = existingUser();
		when(userService.getById("user-1")).thenReturn(existing);

		Result tooShort = controller.editPwd(changePasswordRequest("a1b2c3"));
		Result noDigit = controller.editPwd(changePasswordRequest("abcdefgh"));
		Result weak = controller.editPwd(changePasswordRequest("admin123"));

		assertThat(tooShort.getCode()).isNotZero();
		assertThat(tooShort.getMsg()).contains("8到64");
		assertThat(noDigit.getCode()).isNotZero();
		assertThat(noDigit.getMsg()).contains("数字");
		assertThat(weak.getCode()).isNotZero();
		assertThat(weak.getMsg()).contains("过于简单");
		// 弱密码不得写库，也不得踢线
		verify(userService, never()).updateById(existing);
		verify(revoker, never()).revokeAll("user-1", SessionInvalidSubReason.PASSWORD_CHANGED);
	}

	@Test
	void addRejectsWeakPasswordBeforeHashing() {
		SysUser request = new SysUser();
		request.setUsername("clerk");
		request.setPassword("12345678");
		request.setRoles(List.of("role-1"));

		Result result = controller.add(request);

		assertThat(result.getCode()).isNotZero();
		assertThat(result.getMsg()).contains("字母");
		verify(userService, never()).saveUser(request);
	}

	@Test
	void addHashesStrongPasswordAndPersists() {
		SysUser request = new SysUser();
		request.setUsername("clerk");
		request.setPassword(STRONG_NEW_PASSWORD);
		request.setRoles(List.of("role-1"));
		when(userService.saveUser(request)).thenReturn(true);

		Result result = controller.add(request);

		assertThat(result.getCode()).isZero();
		assertThat(BCrypt.checkpw(STRONG_NEW_PASSWORD, request.getPassword())).isTrue();
		verify(userService).saveUser(request);
	}

	@Test
	void getByIdResponseDoesNotContainPasswordField() throws Exception {
		SysUser existing = existingUser();
		when(userService.getVisibleUserById("user-1")).thenReturn(existing);
		when(sysRoleService.findRoleIdsByUserId("user-1")).thenReturn(List.of("role-1"));

		Result<SysUserVO> result = controller.getById("user-1");

		assertThat(result.getData()).isInstanceOf(SysUserVO.class);
		assertThat(result.getData().getRoles()).containsExactly("role-1");
		String json = new ObjectMapper().writeValueAsString(result.getData());
		assertThat(json).doesNotContain("password");
		assertThat(json).doesNotContain(existing.getPassword());
	}

	@Test
	void infoResponseDoesNotContainPasswordField() throws Exception {
		SysUser existing = existingUser();
		when(userService.getById("user-1")).thenReturn(existing);
		when(userService.findUserInfo(existing)).thenReturn(existing);
		try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
			stpUtil.when(StpUtil::getLoginId).thenReturn("user-1");

			Result<SysUserVO> result = controller.info();

			assertThat(result.getData()).isInstanceOf(SysUserVO.class);
			String json = new ObjectMapper().writeValueAsString(result.getData());
			assertThat(json).doesNotContain("password");
			assertThat(json).doesNotContain(existing.getPassword());
		}
		verify(userService).findUserInfo(existing);
	}

}
