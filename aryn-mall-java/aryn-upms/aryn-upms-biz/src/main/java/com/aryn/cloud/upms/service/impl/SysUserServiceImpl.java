
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.api.vo.MenuVO;
import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.upms.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

	private final SysRoleMapper sysRoleMapper;

	private final SysMenuMapper sysMenuMapper;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final SysRoleMenuMapper sysRoleMenuMapper;

	@Override
	public SysUser findUserInfo(SysUser sysUser) {
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());

		// 查询角色
		List<String> roleIds = sysRoleMapper.listRoleIdsByUserId(sysUser.getId());
		sysUser.setRoles(roleIds);
		// 权限列表
		Set<String> permissions = new HashSet<>();
		roleIds.forEach(roleId -> {
			List<MenuVO> sysMenus = sysMenuMapper.listMenuByRoleId(roleId);
			List<String> permissionList = sysMenus.stream()
				.filter(menuVo -> StrUtil.isNotEmpty(menuVo.getPermission()))
				.map(MenuVO::getPermission)
				.collect(Collectors.toList());
			permissions.addAll(permissionList);
		});
		sysUser.setPermissions(permissions);
		return sysUser;
	}

	@Override
	public SysUser findUserByName(String username) {
		return baseMapper.selectUserByName(username);
	}

	@Override
	public SysUser findUserByPhone(String phone) {
		return baseMapper.selectUserByPhone(phone);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveUser(SysUser sysUser) {
		if (Objects.nonNull(this.findUserByName(sysUser.getUsername()))) {
			throw new ArynBusinessException("用户已存在");
		}
		if (Objects.nonNull(this.findUserByPhone(sysUser.getPhone()))) {
			throw new ArynBusinessException("该手机号已存在");
		}
		baseMapper.insert(sysUser);
		saveUserRole(sysUser);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateUser(SysUser sysUser) {
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		baseMapper.updateById(sysUser);
		saveUserRole(sysUser);
		return Boolean.TRUE;
	}

	@Override
	public IPage<SysUser> adminPage(Page page, SysUser sysUser) {
		return baseMapper.adminPage(page, sysUser);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean delUser(SysUser sysUser) {
		// 删除用户管理角色
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		// 删除用户
		return super.removeById(sysUser.getId());
	}

	@Override
	public int getCount(SysUser sysUser) {
		return baseMapper.selectCount(sysUser);
	}

	private void saveUserRole(SysUser sysUser) {
		SysUserRole sysUserRole = new SysUserRole();
		for (String role : sysUser.getRoles()) {
			sysUserRole.setId(null);
			sysUserRole.setUserId(sysUser.getId());
			sysUserRole.setRoleId(role);
			sysUserRole.insert();
		}
	}

}
