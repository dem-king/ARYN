package com.aryn.cloud.upms.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysRoleMenuMapper;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.impl.SysUserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysUserServiceImplTest {

	@Test
	void saveUserRejectsExistingPhoneBeforeInsert() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysUserServiceImpl service = new SysUserServiceImpl(mock(SysRoleMapper.class), mock(SysMenuMapper.class),
				mock(SysUserRoleMapper.class), mock(SysRoleMenuMapper.class));
		ReflectionTestUtils.setField(service, "baseMapper", userMapper);

		SysUser request = new SysUser();
		request.setUsername("new-user");
		request.setPhone("13279236910");
		when(userMapper.selectUserByName(request.getUsername())).thenReturn(null);
		when(userMapper.selectUserByPhone(request.getPhone())).thenReturn(new SysUser());

		assertThatThrownBy(() -> service.saveUser(request))
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> org.assertj.core.api.Assertions.assertThat(exception.getMsg()).isEqualTo("该手机号已存在"));

		verify(userMapper, never()).insert(request);
	}

}
