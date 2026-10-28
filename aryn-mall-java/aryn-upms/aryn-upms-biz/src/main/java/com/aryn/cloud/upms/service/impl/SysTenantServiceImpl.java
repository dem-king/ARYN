package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.RegisterTenantDTO;
import com.aryn.cloud.upms.api.entity.*;
import com.aryn.cloud.upms.mapper.SysTenantMapper;
import com.aryn.cloud.upms.service.*;
import com.aryn.cloud.upms.support.TenantSessionRevoker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 租户管理
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:51
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements ISysTenantService {

	private static final String DISABLED_STATUS = "1";

	private static final int MAX_RENEW_DAYS = 3650;

	private final ISysRoleService sysRoleService;

	private final ISysDeptService sysDeptService;

	private final ISysUserService sysUserService;

	private final ISysUserRoleService sysUserRoleService;

	private final ISysMenuService sysMenuService;

	private final ISysRoleMenuService sysRoleMenuService;

	private final ISysTenantPackageService sysTenantPackageService;

	private final ISysTenantMenuService sysTenantMenuService;

	private final TenantSessionRevoker tenantSessionRevoker;

	private final CacheManager cacheManager;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveTenant(SysTenant sysTenant) {
		if (sysTenant == null) {
			throw new ArynBusinessException("租户信息不能为空");
		}
		String username = StrUtil.trim(sysTenant.getUsername());
		String phone = StrUtil.trim(sysTenant.getPhone());
		if (StrUtil.isBlank(username) || StrUtil.isBlank(phone) || StrUtil.isEmpty(sysTenant.getPassword())) {
			throw new ArynBusinessException("用户名、手机号和密码不能为空");
		}
		sysTenant.setUsername(username);
		sysTenant.setPhone(phone);
		if (StrUtil.isBlank(sysTenant.getStatus())) {
			sysTenant.setStatus(CommonConstants.NORMAL_STATUS);
		}
		requireAvailablePackage(sysTenant.getPackageId());
		if (Objects.nonNull(sysUserService.findUserByName(username))) {
			throw new ArynBusinessException("该用户已存在: " + username);
		}
		if (Objects.nonNull(sysUserService.findUserByPhone(phone))) {
			throw new ArynBusinessException("该手机号已存在: " + phone);
		}
		super.save(sysTenant);
		this.registerSysTenant(sysTenant, CommonConstants.REGISTER_TYPE_USERNAME);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean register(RegisterTenantDTO registerTenantDTO) {
		if (registerTenantDTO == null || StrUtil.isBlank(registerTenantDTO.getName())) {
			throw new ArynBusinessException("公司名称不能为空");
		}
		String phone = StrUtil.trim(registerTenantDTO.getPhone());
		if (StrUtil.isBlank(phone)) {
			throw new ArynBusinessException("手机号不能为空");
		}
		SysUser user = sysUserService.findUserByPhone(phone);
		if (ObjectUtil.isNotNull(user)) {
			throw new ArynBusinessException("该手机号已注册");
		}
		requireAvailablePackage(registerTenantDTO.getPackageId());
		SysTenant sysTenant = new SysTenant();
		sysTenant.setName(registerTenantDTO.getName().trim());
		sysTenant.setPhone(phone);
		sysTenant.setStatus(CommonConstants.NORMAL_STATUS);
		sysTenant.setPackageId(registerTenantDTO.getPackageId());
		sysTenant.setAuthBeginTime(LocalDateTime.now());
		sysTenant.setAuthEndTime(LocalDateTimeUtil.offset(sysTenant.getAuthBeginTime(), 7, ChronoUnit.DAYS));
		super.save(sysTenant);
		this.registerSysTenant(sysTenant, CommonConstants.REGISTER_TYPE_PHONE);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void registerSysTenant(SysTenant sysTenant, String type) {
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(sysTenant.getId());
		try {
			// 新增角色
			SysRole sysRole = new SysRole();
			sysRole.setRoleCode(CommonConstants.ROLE_ADMIN_CODE);
			sysRole.setRoleName(CommonConstants.ROLE_ADMIN_NAME);
			sysRoleService.save(sysRole);
			// 新增部门
			SysDept sysDept = new SysDept();
			sysDept.setDeptName(sysTenant.getName());
			sysDept.setParentId(CommonConstants.PARENT_ID);
			sysDept.setSort(1);
			sysDeptService.save(sysDept);
			// 新增用户
			SysUser sysUser = new SysUser();
			switch (type) {
				case CommonConstants.REGISTER_TYPE_USERNAME -> sysUser.setUsername(sysTenant.getUsername());
				case CommonConstants.REGISTER_TYPE_PHONE -> sysUser.setUsername(sysTenant.getPhone());
				default -> throw new ArynBusinessException("不支持的租户注册类型");
			}
			sysUser.setPhone(sysTenant.getPhone());
			String rawPassword = sysTenant.getPassword();
			if (StrUtil.isEmpty(rawPassword)) {
				if (CommonConstants.REGISTER_TYPE_USERNAME.equals(type)) {
					throw new ArynBusinessException("密码不能为空");
				}
				// 自助注册入口不采集密码，生成不可用的随机密码；该账号通过短信验证码登录
				rawPassword = IdUtil.fastSimpleUUID();
			}
			sysUser.setPassword(BCrypt.hashpw(rawPassword));
			sysUser.setDeptId(sysDept.getId());
			sysUser.setStatus(CommonConstants.NORMAL_STATUS);
			sysUser.setType(CommonConstants.USER_TYPE_PRIMARY);
			sysUserService.save(sysUser);
			// 查询套餐包
			SysTenantPackage sysTenantPackage = requireAvailablePackage(sysTenant.getPackageId());
			// 租户授权菜单
			List<SysMenu> packageMenus = resolvePackageMenus(sysTenantPackage);
			List<SysTenantMenu> tenantMenuList = packageMenus.stream().map(menu -> {
				SysTenantMenu tenantMenu = new SysTenantMenu();
				tenantMenu.setMenuId(menu.getId());
				return tenantMenu;
			}).collect(Collectors.toList());
			if (!tenantMenuList.isEmpty()) {
				sysTenantMenuService.saveBatch(tenantMenuList);
			}
			// 新增角色关联菜单
			List<SysRoleMenu> roleMenuList = tenantMenuList.stream().map(menu -> {
				SysRoleMenu roleMenu = new SysRoleMenu();
				roleMenu.setRoleId(sysRole.getId());
				roleMenu.setMenuId(menu.getMenuId());
				return roleMenu;
			}).collect(Collectors.toList());
			if (!roleMenuList.isEmpty()) {
				sysRoleMenuService.saveBatch(roleMenuList);
			}

			// 新增用户关联角色
			SysUserRole sysUserRole = new SysUserRole();
			sysUserRole.setUserId(sysUser.getId());
			sysUserRole.setRoleId(sysRole.getId());
			sysUserRoleService.save(sysUserRole);
		}
		finally {
			// 注册过程会切换到新租户；必须还给调用方，否则操作日志等后续逻辑会记到新租户名下
			if (StrUtil.isBlank(previousTenantId)) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateTenant(SysTenant sysTenant) {
		if (sysTenant == null || StrUtil.isBlank(sysTenant.getId())) {
			throw new ArynBusinessException("缺少租户标识");
		}
		SysTenant existing = getById(sysTenant.getId());
		if (existing == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
		boolean platformTenant = CommonConstants.PLATFORM_TENANT_ID.equals(existing.getId());
		if (platformTenant && attemptsToClose(sysTenant)) {
			throw new ArynBusinessException("平台租户不可停用或设置过期的授权时间");
		}
		boolean packageChanged = StrUtil.isNotBlank(sysTenant.getPackageId())
				&& !sysTenant.getPackageId().equals(existing.getPackageId());
		if (packageChanged) {
			requireAvailablePackage(sysTenant.getPackageId());
		}
		boolean updated = updateById(sysTenant);
		if (!updated) {
			return false;
		}
		boolean sessionsRevoked = false;
		if (packageChanged && !platformTenant) {
			// 套餐是租户菜单与角色授权的来源，变更后必须同步裁剪，否则降级后旧权限仍可登录使用
			resyncTenantMenus(existing.getId(), sysTenant.getPackageId());
			revokeSessionsAfterCommit(existing.getId(), "租户套餐变更");
			sessionsRevoked = true;
		}
		SysTenant effective = getById(sysTenant.getId());
		if (!sessionsRevoked && isAccessClosed(effective)) {
			revokeSessionsAfterCommit(existing.getId(), "租户停用或授权到期");
		}
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean removeTenant(String id) {
		if (StrUtil.isBlank(id)) {
			throw new ArynBusinessException("缺少租户标识");
		}
		SysTenant existing = getById(id);
		if (existing == null) {
			return false;
		}
		if (CommonConstants.PLATFORM_TENANT_ID.equals(id)) {
			throw new ArynBusinessException("平台租户不可删除");
		}
		boolean removed = removeById(id);
		if (removed) {
			revokeSessionsAfterCommit(id, "租户删除");
		}
		return removed;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean changeStatus(String id, String status) {
		if (!CommonConstants.NORMAL_STATUS.equals(status) && !DISABLED_STATUS.equals(status)) {
			throw new ArynBusinessException("租户状态不合法");
		}
		SysTenant patch = new SysTenant();
		patch.setId(id);
		patch.setStatus(status);
		return updateTenant(patch);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public LocalDateTime renew(String id, int days) {
		if (days < 1 || days > MAX_RENEW_DAYS) {
			throw new ArynBusinessException("续期天数需在 1-" + MAX_RENEW_DAYS + " 天之间");
		}
		SysTenant existing = getById(id);
		if (existing == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime base = existing.getAuthEndTime() == null || existing.getAuthEndTime().isBefore(now)
				? now : existing.getAuthEndTime();
		LocalDateTime newEnd = base.plusDays(days);
		SysTenant patch = new SysTenant();
		patch.setId(id);
		patch.setAuthEndTime(newEnd);
		if (!updateTenant(patch)) {
			throw new ArynBusinessException("租户续期失败");
		}
		return newEnd;
	}

	@Override
	public int kickoutSessions(String id) {
		SysTenant existing = getById(id);
		if (existing == null) {
			throw new ArynBusinessException("租户不存在或已删除");
		}
		return tenantSessionRevoker.revokeTenant(id, "平台强制下线");
	}

	private List<SysMenu> resolvePackageMenus(SysTenantPackage tenantPackage) {
		Set<SysMenu> menus = new HashSet<>();
		String[] appKeys = tenantPackage.getAppKey();
		if (appKeys != null) {
			Arrays.stream(appKeys)
				.filter(StrUtil::isNotBlank)
				.forEach(key -> menus.addAll(
						sysMenuService.list(Wrappers.<SysMenu>lambdaQuery().eq(SysMenu::getApplicationKey, key))));
		}
		return new ArrayList<>(menus);
	}

	/**
	 * 套餐变更后按新套餐重算租户菜单与角色授权。
	 *
	 * <p>租户菜单以套餐为唯一来源；角色菜单只保留仍在套餐内的部分，
	 * 管理员角色补全新套餐菜单。降级后原角色菜单必须同步裁剪，
	 * 否则旧权限在重新登录后仍会进入权限快照。
	 */
	private void resyncTenantMenus(String tenantId, String packageId) {
		List<SysMenu> packageMenus = resolvePackageMenus(requireAvailablePackage(packageId));
		if (packageMenus.isEmpty()) {
			throw new ArynBusinessException("新套餐未配置任何菜单，无法变更");
		}
		Set<String> authorizedMenuIds = packageMenus.stream()
			.map(SysMenu::getId)
			.collect(Collectors.toSet());
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			List<SysTenantMenu> existingMenus = sysTenantMenuService.list();
			Set<String> existingMenuIds = existingMenus.stream()
				.map(SysTenantMenu::getMenuId)
				.collect(Collectors.toSet());
			List<String> staleIds = existingMenus.stream()
				.filter(menu -> !authorizedMenuIds.contains(menu.getMenuId()))
				.map(SysTenantMenu::getId)
				.toList();
			if (!staleIds.isEmpty()) {
				sysTenantMenuService.removeByIds(staleIds);
			}
			List<SysTenantMenu> missingMenus = authorizedMenuIds.stream()
				.filter(menuId -> !existingMenuIds.contains(menuId))
				.map(menuId -> {
					SysTenantMenu tenantMenu = new SysTenantMenu();
					tenantMenu.setMenuId(menuId);
					return tenantMenu;
				})
				.toList();
			if (!missingMenus.isEmpty()) {
				sysTenantMenuService.saveBatch(missingMenus);
			}
			syncRoleMenus(authorizedMenuIds);
		}
		finally {
			if (StrUtil.isBlank(previousTenantId)) {
				ArynTenantContextHolder.removeTenantId();
			}
			else {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
		}
		Cache menuCache = cacheManager.getCache(CacheConstants.MENU_CACHE);
		if (menuCache != null) {
			menuCache.clear();
		}
	}

	private void syncRoleMenus(Set<String> authorizedMenuIds) {
		for (SysRole role : sysRoleService.list()) {
			Set<String> currentMenuIds = sysRoleMenuService
				.list(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, role.getId()))
				.stream()
				.map(SysRoleMenu::getMenuId)
				.collect(Collectors.toSet());
			Set<String> retainedMenuIds = new HashSet<>(currentMenuIds);
			retainedMenuIds.retainAll(authorizedMenuIds);
			// 与注册逻辑一致：管理员角色始终持有套餐内全部菜单
			Set<String> targetMenuIds = CommonConstants.ROLE_ADMIN_CODE.equals(role.getRoleCode())
					? authorizedMenuIds : retainedMenuIds;
			List<String> removedMenuIds = currentMenuIds.stream()
				.filter(menuId -> !targetMenuIds.contains(menuId))
				.toList();
			if (!removedMenuIds.isEmpty()) {
				sysRoleMenuService.remove(Wrappers.<SysRoleMenu>lambdaQuery()
					.eq(SysRoleMenu::getRoleId, role.getId())
					.in(SysRoleMenu::getMenuId, removedMenuIds));
			}
			List<SysRoleMenu> addedMenus = targetMenuIds.stream()
				.filter(menuId -> !currentMenuIds.contains(menuId))
				.map(menuId -> {
					SysRoleMenu roleMenu = new SysRoleMenu();
					roleMenu.setRoleId(role.getId());
					roleMenu.setMenuId(menuId);
					return roleMenu;
				})
				.toList();
			if (!addedMenus.isEmpty()) {
				sysRoleMenuService.saveBatch(addedMenus);
			}
		}
	}

	private SysTenantPackage requireAvailablePackage(String packageId) {
		if (StrUtil.isBlank(packageId)) {
			throw new ArynBusinessException("请选择租户套餐");
		}
		SysTenantPackage sysTenantPackage = sysTenantPackageService.getById(packageId);
		if (ObjectUtil.isNull(sysTenantPackage)) {
			throw new ArynBusinessException("套餐包不存在");
		}
		if (!CommonConstants.NORMAL_STATUS.equals(sysTenantPackage.getStatus())) {
			throw new ArynBusinessException("套餐包已停用");
		}
		return sysTenantPackage;
	}

	private boolean attemptsToClose(SysTenant sysTenant) {
		if (sysTenant.getStatus() != null && !CommonConstants.NORMAL_STATUS.equals(sysTenant.getStatus())) {
			return true;
		}
		LocalDateTime now = LocalDateTime.now();
		return (sysTenant.getAuthEndTime() != null && sysTenant.getAuthEndTime().isBefore(now))
				|| (sysTenant.getAuthBeginTime() != null && sysTenant.getAuthBeginTime().isAfter(now));
	}

	private boolean isAccessClosed(SysTenant tenant) {
		if (tenant == null) {
			return true;
		}
		// 平台租户是运维入口，必须始终可登录，避免误配把平台自身锁死
		if (CommonConstants.PLATFORM_TENANT_ID.equals(tenant.getId())) {
			return false;
		}
		if (!CommonConstants.NORMAL_STATUS.equals(tenant.getStatus())) {
			return true;
		}
		LocalDateTime now = LocalDateTime.now();
		if (tenant.getAuthBeginTime() != null && tenant.getAuthBeginTime().isAfter(now)) {
			return true;
		}
		return tenant.getAuthEndTime() != null && tenant.getAuthEndTime().isBefore(now);
	}

	private void revokeSessionsAfterCommit(String tenantId, String reason) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					tenantSessionRevoker.revokeTenant(tenantId, reason);
				}
			});
			return;
		}
		tenantSessionRevoker.revokeTenant(tenantId, reason);
	}

}
