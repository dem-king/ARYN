package com.aryn.cloud.upms.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.dto.SysUserDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserControllerPasswordTest {

	@Test
	void editPasswordVerifiesOldPasswordAndStoresNewPasswordHash() {
		ISysUserService userService = mock(ISysUserService.class);
		SysUserSessionRevoker revoker = mock(SysUserSessionRevoker.class);
		SysUserController controller = new SysUserController(userService, mock(ISysRoleService.class),
				mock(ISysUserRoleService.class), revoker, mock(RemoteDeliveryAccountService.class));
		SysUser existing = new SysUser();
		existing.setId("user-1");
		existing.setPassword(BCrypt.hashpw("old-password"));
		when(userService.getById("user-1")).thenReturn(existing);
		when(userService.updateById(existing)).thenReturn(true);

		SysUserDTO request = new SysUserDTO();
		request.setId("user-1");
		request.setPassword("old-password");
		request.setNewPassword("new-password");
		request.setCheckPassword("new-password");

		controller.editPwd(request);

		ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
		verify(userService).updateById(captor.capture());
		assertThat(BCrypt.checkpw("new-password", captor.getValue().getPassword())).isTrue();
		assertThat(BCrypt.checkpw("old-password", captor.getValue().getPassword())).isFalse();
		// 改密即失效：删除该账号所有已登录会话
		verify(revoker).revokeAll("user-1", "修改密码");
	}

	@Test
	void editPasswordDoesNotRevokeSessionsWhenPasswordUpdateFails() {
		ISysUserService userService = mock(ISysUserService.class);
		SysUserSessionRevoker revoker = mock(SysUserSessionRevoker.class);
		SysUserController controller = new SysUserController(userService, mock(ISysRoleService.class),
				mock(ISysUserRoleService.class), revoker, mock(RemoteDeliveryAccountService.class));
		SysUser existing = new SysUser();
		existing.setId("user-1");
		existing.setPassword(BCrypt.hashpw("old-password"));
		when(userService.getById("user-1")).thenReturn(existing);
		when(userService.updateById(existing)).thenReturn(false);

		SysUserDTO request = new SysUserDTO();
		request.setId("user-1");
		request.setPassword("old-password");
		request.setNewPassword("new-password");
		request.setCheckPassword("new-password");

		controller.editPwd(request);

		// 密码没有实际写库时不能把用户踢下线
		verify(revoker, never()).revokeAll("user-1", "修改密码");
	}

}
