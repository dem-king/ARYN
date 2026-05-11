
package com.aryn.cloud.upms.dubbo;

import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.upms.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysUserServiceImpl implements RemoteSysUserService {

	private final ISysUserService sysUserService;

	@Override
	public SysUser getUserInfo(String username) {
		// 首先通过用户名获取用户信息
		SysUser sysUser = sysUserService.findUserByName(username);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		// 从用户信息中获取租户ID，设置到线程变量中
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public SysUser getUserInfoByPhone(String phone) {
		// 首先通过手机号获取用户信息
		SysUser sysUser = sysUserService.findUserByPhone(phone);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		// 从用户信息中获取租户ID，设置到线程变量中
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());
		return sysUserService.findUserInfo(sysUser);
	}

}
