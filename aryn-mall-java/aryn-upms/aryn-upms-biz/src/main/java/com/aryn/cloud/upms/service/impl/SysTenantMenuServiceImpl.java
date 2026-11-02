
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.SysTenantMenuDTO;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.entity.SysTenantMenu;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysTenantMenuMapper;
import com.aryn.cloud.upms.service.ISysTenantMenuService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户分配菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:51
 */
@Service
@AllArgsConstructor
public class SysTenantMenuServiceImpl extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu>
		implements ISysTenantMenuService {

	private final SysMenuMapper sysMenuMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveTenantMenu(SysTenantMenuDTO sysTenantMenuDTO) {
		// 全量替换前必须有明确租户上下文，避免无上下文时误删其它租户的授权行
		if (StrUtil.isBlank(ArynTenantContextHolder.getTenantId())) {
			throw new ArynBusinessException(403, "缺少租户上下文");
		}
		List<String> menuIds = sysTenantMenuDTO.getMenuIds() == null ? List.of() : sysTenantMenuDTO.getMenuIds();
		rejectPlatformMenus(menuIds);
		this.remove(Wrappers.query());
		List<SysTenantMenu> sysTenantMenuList = menuIds.stream().map(v -> {
			SysTenantMenu sysTenantMenu = new SysTenantMenu();
			sysTenantMenu.setMenuId(v);
			return sysTenantMenu;
		}).collect(Collectors.toList());
		return this.saveBatch(sysTenantMenuList);
	}

	/**
	 * 平台应用菜单只服务平台运营，不能被分配给任何租户；前端置灰只是提示，接口必须自行拦截。
	 */
	private void rejectPlatformMenus(List<String> menuIds) {
		if (menuIds.isEmpty()) {
			return;
		}
		Long platformMenuCount = sysMenuMapper.selectCount(Wrappers.<SysMenu>lambdaQuery()
			.in(SysMenu::getId, menuIds)
			.eq(SysMenu::getApplicationKey, CommonConstants.PLATFORM_APP_KEY));
		if (platformMenuCount != null && platformMenuCount > 0) {
			throw new ArynBusinessException("平台应用菜单不可分配给租户");
		}
	}

}
