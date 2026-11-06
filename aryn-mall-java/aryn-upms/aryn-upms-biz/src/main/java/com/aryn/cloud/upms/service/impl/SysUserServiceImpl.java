
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
import com.aryn.cloud.upms.api.entity.SysDept;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.api.vo.MenuVO;
import com.aryn.cloud.upms.api.vo.SysUserVO;
import com.aryn.cloud.upms.mapper.*;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.upms.support.PlatformAdminSupport;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
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

	private final SysTenantMapper sysTenantMapper;

	private final SysUserSessionRevoker sysUserSessionRevoker;

	private final PlatformAdminSupport platformAdminSupport;

	private final SysDeptMapper sysDeptMapper;

	@Override
	public SysUser findUserInfo(SysUser sysUser) {
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());

		// 平台身份以数据为准：租户类型 + 用户级 platform_admin 标记，前端与守卫不再硬编码租户 ID
		SysTenant tenant = StrUtil.isBlank(sysUser.getTenantId()) ? null
				: sysTenantMapper.selectById(sysUser.getTenantId());
		sysUser.setTenantType(tenant == null ? null : tenant.getTenantType());
		String platformAdminFlag = StrUtil.isBlank(sysUser.getId()) ? null
				: baseMapper.selectPlatformAdminFlag(sysUser.getId());
		sysUser.setPlatformAdmin(CommonConstants.TENANT_TYPE_PLATFORM.equals(sysUser.getTenantType())
				&& CommonConstants.YES.equals(platformAdminFlag));

		// 部门名称是 sys_dept 的关联展示字段（SysUser.deptName 非表字段），
		// 按 ID 直接查库不会带出；员工详情/远程 RPC 查询必须一并回填，
		// 否则下游（如销售员登记）拿到的 deptName 恒为 null。
		if (StrUtil.isNotBlank(sysUser.getDeptId())) {
			SysDept dept = sysDeptMapper.selectById(sysUser.getDeptId());
			sysUser.setDeptName(dept == null ? null : dept.getDeptName());
		}

		// 查询角色
		List<String> roleIds = sysRoleMapper.listRoleIdsByUserId(sysUser.getId());
		sysUser.setRoles(roleIds);
		// 权限列表：平台管理员是系统内置身份，权限恒为「全部可见菜单」，不依赖可编辑的
		// sys_role_menu 数据；其余账号按角色授权计算
		Set<String> permissions = new HashSet<>();
		if (Boolean.TRUE.equals(sysUser.getPlatformAdmin())) {
			permissions.addAll(sysMenuMapper.selectAllPermissions());
		}
		else {
			roleIds.forEach(roleId -> {
				List<MenuVO> sysMenus = sysMenuMapper.listMenuByRoleId(roleId);
				List<String> permissionList = sysMenus.stream()
					.filter(menuVo -> StrUtil.isNotEmpty(menuVo.getPermission()))
					.map(MenuVO::getPermission)
					.collect(Collectors.toList());
				permissions.addAll(permissionList);
			});
		}
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
		// 平台管理员角色只允许平台管理员授予
		assertCanAssignRoles(sysUser.getRoles());
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
		// 停用前置状态：仅当本次把账号从「正常」改为「停用」时才需要撤销已有会话
		SysUser existing = baseMapper.selectById(sysUser.getId());
		// 平台管理员账号只允许平台管理员维护；平台管理员角色只允许平台管理员授予
		assertCanManageUser(existing);
		assertCanAssignRoles(sysUser.getRoles());
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
		// 停用即下线：账号已不可再次登录，但已签发的 token 仍会有效到过期，必须立即撤销
		if (isDisabledTransition(existing, sysUser)) {
			sysUserSessionRevoker.revokeAll(sysUser.getId(), SessionInvalidSubReason.ACCOUNT_DISABLED);
		}
		return Boolean.TRUE;
	}

	@Override
	public IPage<SysUserVO> adminPage(Page page, SysUser sysUser) {
		return baseMapper.adminPage(page, sysUser);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean delUser(SysUser sysUser) {
		// 平台管理员账号只允许平台管理员删除
		assertCanManageUser(baseMapper.selectById(sysUser.getId()));
		// 删除用户管理角色
		sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, sysUser.getId()));
		// 删除用户
		boolean removed = super.removeById(sysUser.getId());
		if (removed) {
			// 删除即下线：避免已删除账号的旧 token 继续访问管理端/配送端
			sysUserSessionRevoker.revokeAll(sysUser.getId(), SessionInvalidSubReason.ACCOUNT_DELETED);
		}
		return removed;
	}

	@Override
	public int getCount(SysUser sysUser) {
		return baseMapper.countUserByQuery(sysUser);
	}

	@Override
	public long countVisibleStaff() {
		return baseMapper.countTenantStaff();
	}

	@Override
	public SysUser getVisibleUserById(String userId) {
		SysUser user = getById(userId);
		if (user != null && CommonConstants.YES.equals(baseMapper.selectPlatformAdminFlag(userId))) {
			// 平台管理员账号是系统内置账号，任何角色账号都不应看到它的存在
			return null;
		}
		return user;
	}

	/**
	 * 本次编辑是否把账号从「正常」改为「停用」。
	 * <p>status 为 null 表示本次不更新该字段（MyBatis-Plus not_null 策略），不触发会话撤销；
	 * 停用状态值以 {@link CommonConstants#NORMAL_STATUS} 为准（"0" 正常，其余视为停用）。
	 */
	private static boolean isDisabledTransition(SysUser existing, SysUser updated) {
		if (existing == null || updated == null || updated.getStatus() == null) {
			return false;
		}
		return CommonConstants.NORMAL_STATUS.equals(existing.getStatus())
				&& !CommonConstants.NORMAL_STATUS.equals(updated.getStatus());
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

	/**
	 * 平台管理员账号只允许平台管理员维护（改资料、停用、删除、改角色）。
	 *
	 * <p>平台租户里的租户管理员即使拥有用户管理权限，也不能改动平台管理员账号，否则
	 * 可停用/删除平台唯一运维入口或改写其角色。</p>
	 */
	private void assertCanManageUser(SysUser target) {
		if (target == null || platformAdminSupport.isCurrentPlatformAdmin()) {
			return;
		}
		if (CommonConstants.YES.equals(baseMapper.selectPlatformAdminFlag(target.getId()))) {
			throw new ArynBusinessException(403, "平台管理员账号仅平台管理员可维护");
		}
	}

	/**
	 * 平台管理员角色只允许平台管理员授予，避免租户管理员借角色拿到平台菜单与权限。
	 */
	private void assertCanAssignRoles(List<String> roleIds) {
		if (CollUtil.isEmpty(roleIds) || platformAdminSupport.isCurrentPlatformAdmin()) {
			return;
		}
		List<String> platformRoleIds = sysRoleMapper.selectPlatformAdminRoleIds();
		if (CollUtil.isEmpty(platformRoleIds)) {
			return;
		}
		if (CollUtil.containsAny(roleIds, platformRoleIds)) {
			throw new ArynBusinessException(403, "无权授予平台管理员角色");
		}
	}

}
