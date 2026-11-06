
package com.aryn.cloud.upms.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.vo.MenuVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 菜单
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:46
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

	/**
	 * 通过角色主键查询菜单
	 *
	 * @author 雨滴kian
	 * @date 2022/7/18
	 * @param roleId
	 * @return: java.util.List<com.aryn.cloud.upms.common.vo.MenuVO>
	 */
	List<MenuVO> listMenuByRoleId(String roleId);

	/**
	 * 查询全部可见菜单。
	 *
	 * <p>平台管理员的菜单树按「全部菜单」动态推导，不依赖可编辑的 {@code sys_role_menu} 数据，
	 * 避免角色菜单被误改后平台管理员丢失菜单。</p>
	 */
	List<MenuVO> listAllMenus();

	/**
	 * 查询全部可见菜单的权限串（平台管理员权限动态推导用）。
	 */
	@Select("SELECT DISTINCT `permission` FROM `sys_menu` WHERE `del_flag` = '0' "
			+ "AND `permission` IS NOT NULL AND `permission` != ''")
	List<String> selectAllPermissions();

	/**
	 * 查询租户菜单树
	 * @param tenantId
	 * @return
	 */
	@InterceptorIgnore(tenantLine = "true")
	List<SysMenu> selectTenantMenuTree(String tenantId);

}
