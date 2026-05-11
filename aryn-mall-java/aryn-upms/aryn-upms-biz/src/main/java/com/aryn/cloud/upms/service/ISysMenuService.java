
package com.aryn.cloud.upms.service;

import cn.hutool.core.lang.tree.TreeNode;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.vo.MenuVO;

import java.util.List;

/**
 * 系统菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysMenuService extends IService<SysMenu> {

	/**
	 * 通过角色主键查询菜单
	 * @param roleId
	 * @author 雨滴kian
	 * @date 2022/7/18
	 * @return: java.util.List<com.aryn.cloud.upms.common.vo.MenuVO>
	 */
	List<MenuVO> findMenuByRoleId(String roleId);

	/**
	 * 保存
	 *
	 * @author: aryn
	 * @date: 2023/4/27 13:32
	 * @param: [sysMenu]
	 * @return: boolean
	 **/
	boolean saveMenu(SysMenu sysMenu);

	/**
	 * 删除
	 *
	 * @author: aryn
	 * @date: 2023/4/27 13:32
	 * @param: [id]
	 * @return: boolean
	 **/
	boolean removeMenuById(String id);

	/**
	 * 修改
	 *
	 * @author: aryn
	 * @date: 2023/4/27 13:32
	 * @param: [sysMenu]
	 * @return: boolean
	 **/
	boolean updateMenuById(SysMenu sysMenu);

	/**
	 * 查询登录用户菜单树
	 * @return
	 */
	List<TreeNode<String>> getLoginUserMenuTree();

	/**
	 * 查询租户下的菜单树
	 * @param tenantId
	 * @return
	 */
	List<TreeNode<String>> getTenantMenuTree(String tenantId);

}
