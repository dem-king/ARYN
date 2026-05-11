
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysRole;

import java.util.List;

/**
 * 角色
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysRoleService extends IService<SysRole> {

	/**
	 * 验证方法
	 *
	 * @author 雨滴kian
	 * @date 2022/9/2
	 * @param sysRole
	 * @return: boolean
	 */
	boolean checkRole(SysRole sysRole);

	/**
	 * 通过用户id查询角色
	 *
	 * @author 雨滴kian
	 * @date 2022/9/2
	 * @param userId
	 * @return: java.util.List<java.lang.String>
	 */
	List<String> findRoleIdsByUserId(String userId);

	/**
	 * 分页查询
	 * @param page
	 * @param sysRole
	 * @return
	 */
	IPage<SysRole> getPage(Page page, SysRole sysRole);

}
