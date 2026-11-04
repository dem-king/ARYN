package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.SysRoleMenuDTO;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysRoleMenu;
import com.aryn.cloud.upms.api.entity.SysTenantMenu;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysRoleMenuMapper;
import com.aryn.cloud.upms.mapper.SysTenantMenuMapper;
import com.aryn.cloud.upms.service.ISysRoleMenuService;
import com.aryn.cloud.upms.support.PlatformAdminSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色关联菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@RequiredArgsConstructor
public class SysRoleMenuServiceImpl extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> implements ISysRoleMenuService {

	private final SysRoleMapper sysRoleMapper;

	private final SysMenuMapper sysMenuMapper;

	private final SysTenantMenuMapper sysTenantMenuMapper;

	private final PlatformAdminSupport platformAdminSupport;

	@Override
	@Transactional(rollbackFor = Exception.class)
	@CacheEvict(value = CacheConstants.MENU_CACHE, allEntries = true)
	public boolean saveRoleMenu(SysRoleMenuDTO request) {
		if (request == null || StrUtil.isBlank(request.getRoleId())) {
			throw new ArynBusinessException("角色不能为空");
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (StrUtil.isBlank(tenantId)) {
			throw new ArynBusinessException(403, "缺少租户上下文");
		}
		// 角色必须属于当前租户；租户拦截器保证 selectById 不会命中其它租户
		SysRole role = sysRoleMapper.selectById(request.getRoleId());
		if (role == null) {
			throw new ArynBusinessException("角色不存在或不属于当前租户");
		}
		// 平台管理员角色持有全部菜单与权限，只允许平台管理员维护，避免租户管理员削权/扩权
		if (CommonConstants.ROLE_PLATFORM_ADMIN_CODE.equals(role.getRoleCode())
				&& !platformAdminSupport.isCurrentPlatformAdmin()) {
			throw new ArynBusinessException(403, "平台管理员角色仅平台管理员可维护");
		}
		List<String> menuIds = request.getMenuIds() == null ? List.of()
				: request.getMenuIds().stream()
					.filter(StrUtil::isNotBlank)
					.map(String::trim)
					.distinct()
					.toList();
		assertMenusAuthorized(tenantId, menuIds);
		LocalDateTime now = LocalDateTime.now();
		super.remove(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, request.getRoleId()));
		List<SysRoleMenu> sysRoleMenus = menuIds.stream().map(menuId -> {
			SysRoleMenu roleMenu = new SysRoleMenu();
			roleMenu.setRoleId(request.getRoleId());
			roleMenu.setMenuId(menuId);
			roleMenu.setId(IdUtil.getSnowflakeNextIdStr());
			roleMenu.setTenantId(tenantId);
			roleMenu.setCreateTime(now);
			return roleMenu;
		}).collect(Collectors.toList());

		// 每批1000条
		int batchSize = 1000;
		for (int i = 0; i < sysRoleMenus.size(); i += batchSize) {
			int end = Math.min(i + batchSize, sysRoleMenus.size());
			List<SysRoleMenu> batchList = sysRoleMenus.subList(i, end);
			baseMapper.saveBatch(batchList);
		}
		return Boolean.TRUE;
	}

	/**
	 * 租户管理员只能把「租户已获授权」的菜单授予角色；平台管理员不受套餐限制。
	 *
	 * <p>否则可绕过套餐直接提交套餐外菜单 ID，重新登录后权限快照即包含越权菜单。
	 */
	private void assertMenusAuthorized(String tenantId, List<String> menuIds) {
		// 平台管理员不受套餐限制（平台管理员是用户级身份，与角色所属租户无关）
		if (platformAdminSupport.isCurrentPlatformAdmin()) {
			if (!menuIds.isEmpty() && sysMenuMapper.selectBatchIds(menuIds).size() != menuIds.size()) {
				throw new ArynBusinessException("存在无效菜单，请刷新后重试");
			}
			return;
		}
		Set<String> authorizedMenuIds = sysTenantMenuMapper
			.selectList(Wrappers.<SysTenantMenu>lambdaQuery().select(SysTenantMenu::getMenuId))
			.stream()
			.map(SysTenantMenu::getMenuId)
			.collect(Collectors.toSet());
		List<String> unauthorized = menuIds.stream().filter(menuId -> !authorizedMenuIds.contains(menuId)).toList();
		if (!unauthorized.isEmpty()) {
			throw new ArynBusinessException("存在超出当前租户套餐授权的菜单，请刷新后重试");
		}
		if (!menuIds.isEmpty()) {
			List<SysMenu> menus = sysMenuMapper.selectBatchIds(menuIds);
			if (menus.size() != menuIds.size()) {
				throw new ArynBusinessException("存在无效菜单，请刷新后重试");
			}
		}
	}

}
