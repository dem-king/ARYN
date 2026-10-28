
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
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
		// 全量替换前必须有明确租户上下文，避免无上下文时误删其它租户的授权行
		if (StrUtil.isBlank(ArynTenantContextHolder.getTenantId())) {
			throw new ArynBusinessException(403, "缺少租户上下文");
		}
		this.remove(Wrappers.query());
		List<SysTenantMenu> sysTenantMenuList = sysTenantMenuDTO.getMenuIds().stream().map(v -> {
			SysTenantMenu sysTenantMenu = new SysTenantMenu();
			sysTenantMenu.setMenuId(v);
			return sysTenantMenu;
		}).collect(Collectors.toList());
		return this.saveBatch(sysTenantMenuList);
	}

}
