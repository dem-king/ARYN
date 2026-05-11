
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.dto.SysTenantMenuDTO;
import com.aryn.cloud.upms.api.entity.SysTenantMenu;
import com.aryn.cloud.upms.mapper.SysTenantMenuMapper;
import com.aryn.cloud.upms.service.ISysTenantMenuService;
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
public class SysTenantMenuServiceImpl extends ServiceImpl<SysTenantMenuMapper, SysTenantMenu>
		implements ISysTenantMenuService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveTenantMenu(SysTenantMenuDTO sysTenantMenuDTO) {
		this.remove(Wrappers.query());
		List<SysTenantMenu> sysTenantMenuList = sysTenantMenuDTO.getMenuIds().stream().map(v -> {
			SysTenantMenu sysTenantMenu = new SysTenantMenu();
			sysTenantMenu.setMenuId(v);
			return sysTenantMenu;
		}).collect(Collectors.toList());
		return this.saveBatch(sysTenantMenuList);
	}

}
