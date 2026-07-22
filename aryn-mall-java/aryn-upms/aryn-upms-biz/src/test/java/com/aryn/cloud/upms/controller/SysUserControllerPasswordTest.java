package com.aryn.cloud.upms.controller;

import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.upms.api.dto.SysUserDTO;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserControllerPasswordTest {

	@Test
	void editPasswordVerifiesOldPasswordAndStoresNewPasswordHash() {
		ISysUserService userService = mock(ISysUserService.class);
		SysUserController controller = new SysUserController(userService, mock(ISysRoleService.class),
				mock(ISysUserRoleService.class));
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
	}

}
