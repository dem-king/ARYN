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
		// 平台租户只能由初始化脚本产生，接口创建一律是普通租户
		sysTenant.setTenantType(CommonConstants.TENANT_TYPE_STANDARD);
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
		sysTenant.setTenantType(CommonConstants.TENANT_TYPE_STANDARD);
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
		if (sysTenant.getTenantType() != null && !sysTenant.getTenantType().equals(existing.getTenantType())) {
			throw new ArynBusinessException("租户类型不可修改");
		}
		boolean platformTenant = isPlatformTenant(existing);
		if (platformTenant && (sysTenant.getAuthEndTime() != null || attemptsToClose(sysTenant))) {
			throw new ArynBusinessException("平台租户长期有效，不可停用或设置授权结束时间");
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
		if (isPlatformTenant(existing)) {
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
		if (isPlatformTenant(existing)) {
			throw new ArynBusinessException("平台租户长期有效，无需续期");
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
		List<String> licensedAppKeys = tenantPackage.getAppKey() == null ? List.of()
				: Arrays.stream(tenantPackage.getAppKey())
					.filter(StrUtil::isNotBlank)
					.filter(key -> {
						// 历史套餐可能已写入平台应用，下发时必须兜底剔除，否则平台菜单会随模板发给租户
						if (CommonConstants.PLATFORM_APP_KEY.equals(key)) {
							log.warn("租户套餐 {} 包含平台专属应用 {}，已忽略", tenantPackage.getId(), key);
							return false;
						}
						return true;
					})
					.distinct()
					.toList();
		if (licensedAppKeys.isEmpty()) {
			return List.of();
		}
		Set<SysMenu> menus = new HashSet<>(sysMenuService
			.list(Wrappers.<SysMenu>lambdaQuery().in(SysMenu::getApplicationKey, licensedAppKeys)));
		return new ArrayList<>(withAncestorContainers(menus));
	}

	/**
	 * 补齐套餐菜单的祖先容器节点。
	 *
	 * <p>套餐按 application_key 取菜单，而管理端菜单树要求父节点存在：若子菜单的
	 * application_key 与父目录不同（例如基础版 app_base 的「船供活动」挂在 app_market 的
	 * 「营销管理」下），父目录未随套餐下发时登录菜单树会丢弃整棵子树。这里沿 parent_id
	 * 向上补齐容器节点，保证「基础版也要船供」等跨应用系统挂载仍然可见。
	 * 平台专属应用（sys_key）的祖先仍不下发给租户。</p>
	 */
	private Set<SysMenu> withAncestorContainers(Set<SysMenu> packageMenus) {
		if (packageMenus.isEmpty()) {
			return packageMenus;
		}
		Map<String, SysMenu> allMenus = sysMenuService.list()
			.stream()
			.collect(Collectors.toMap(SysMenu::getId, menu -> menu, (left, right) -> left));
		// 按 id 去重：不同查询返回的是不同对象实例，不能依赖实体 equals
		Map<String, SysMenu> resolved = new LinkedHashMap<>();
		packageMenus.forEach(menu -> resolved.putIfAbsent(menu.getId(), menu));
		Deque<String> pendingParents = packageMenus.stream()
			.map(SysMenu::getParentId)
			.filter(StrUtil::isNotBlank)
			.collect(Collectors.toCollection(ArrayDeque::new));
		while (!pendingParents.isEmpty()) {
			String parentId = pendingParents.poll();
			if (CommonConstants.PARENT_ID.equals(parentId)) {
				continue;
			}
			SysMenu parent = allMenus.get(parentId);
			if (parent == null) {
				continue;
			}
			if (CommonConstants.PLATFORM_APP_KEY.equals(parent.getApplicationKey())) {
				log.warn("套餐菜单的祖先目录 {} 属于平台专属应用，不下发给租户", parent.getId());
				continue;
			}
			if (resolved.putIfAbsent(parent.getId(), parent) == null
					&& StrUtil.isNotBlank(parent.getParentId())) {
				pendingParents.add(parent.getParentId());
			}
		}
		return new LinkedHashSet<>(resolved.values());
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
			// 平台管理员角色持有全部菜单，不随租户套餐裁剪
			if (CommonConstants.ROLE_PLATFORM_ADMIN_CODE.equals(role.getRoleCode())) {
				continue;
			}
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
		if (isPlatformTenant(tenant)) {
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

	private boolean isPlatformTenant(SysTenant tenant) {
		return tenant != null && CommonConstants.TENANT_TYPE_PLATFORM.equals(tenant.getTenantType());
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
