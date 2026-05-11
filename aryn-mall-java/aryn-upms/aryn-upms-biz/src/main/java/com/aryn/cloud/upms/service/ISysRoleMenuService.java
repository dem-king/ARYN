
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.dto.SysRoleMenuDTO;
import com.aryn.cloud.upms.api.entity.SysRoleMenu;

/**
 * 角色关联菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysRoleMenuService extends IService<SysRoleMenu> {

	boolean saveRoleMenu(SysRoleMenuDTO request);

}
