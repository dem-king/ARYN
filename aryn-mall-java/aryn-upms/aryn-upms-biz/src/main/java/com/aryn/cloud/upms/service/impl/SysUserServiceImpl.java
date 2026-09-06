
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.api.vo.MenuVO;
import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

	private final ISysUserRoleService sysUserRoleService;

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
		// 配送资格只能由配送员管理开通，通用用户新增不允许直接授予受保护角色
		List<String> protectedRoleIds = findProtectedDeliveryRoleIds();
		if (containsAny(sysUser.getRoles(), protectedRoleIds)) {
			throw new ArynBusinessException("不能直接授予配送员角色，请在配送员管理中开通配送资格");
		}
		baseMapper.insert(sysUser);
		saveUserRole(sysUser);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateUser(SysUser sysUser) {
		// 请求列表可能不可变，拷贝后再做受保护角色剔除与合并
		List<String> roles = new ArrayList<>(sysUser.getRoles() == null ? List.of() : sysUser.getRoles());
		sysUser.setRoles(roles);
		List<String> protectedRoleIds = findProtectedDeliveryRoleIds();
		if (CollUtil.isNotEmpty(protectedRoleIds)) {
			// 受保护配送角色不可经通用接口授予或回收：先从请求中剔除
			roles.removeAll(protectedRoleIds);
			// 编辑普通字段时保留已有配送资格，不因前端隐藏复选框而丢失
			List<String> existingProtectedRoleIds = sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
					.eq(SysUserRole::getUserId, sysUser.getId())
					.in(SysUserRole::getRoleId, protectedRoleIds))
				.stream().map(SysUserRole::getRoleId).distinct().collect(Collectors.toList());
			existingProtectedRoleIds.forEach(roleId -> {
				if (!roles.contains(roleId)) {
					roles.add(roleId);
				}
			});
			// 仅重建非受保护关联，受保护关联交给 grantRole 幂等保持，避免误回收
			sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery()
				.eq(SysUserRole::getUserId, sysUser.getId())
				.notIn(SysUserRole::getRoleId, protectedRoleIds));
		}
		else {
			sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		}
		if (CollUtil.isEmpty(roles)) {
			throw new ArynBusinessException("角色不能为空");
		}
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
		// 复用授予逻辑：已逻辑删除的关联恢复原行，避免重复编辑产生无限历史行
		for (String role : sysUser.getRoles()) {
			sysUserRoleService.grantRole(sysUser.getId(), role);
		}
	}

	/**
	 * 查询当前租户受保护的配送资格角色ID列表（同一编码存在重复角色时全部纳入保护）
	 */
	private List<String> findProtectedDeliveryRoleIds() {
		return sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
				.eq(SysRole::getRoleCode, CommonConstants.PROTECTED_DELIVERY_ROLE_CODE))
			.stream().map(SysRole::getId).collect(Collectors.toList());
	}

	private boolean containsAny(List<String> roleIds, List<String> protectedRoleIds) {
		return CollUtil.containsAny(roleIds == null ? new ArrayList<String>() : roleIds, protectedRoleIds);
	}

}
