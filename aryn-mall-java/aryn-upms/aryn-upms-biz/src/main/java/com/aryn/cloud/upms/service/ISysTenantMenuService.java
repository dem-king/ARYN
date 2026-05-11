
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.dto.SysTenantMenuDTO;
import com.aryn.cloud.upms.api.entity.SysTenantMenu;

/**
 * 租户分配菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysTenantMenuService extends IService<SysTenantMenu> {

	boolean saveTenantMenu(SysTenantMenuDTO sysTenantMenuDTO);

}
