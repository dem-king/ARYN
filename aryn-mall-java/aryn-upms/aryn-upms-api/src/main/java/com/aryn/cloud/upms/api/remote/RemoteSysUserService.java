
package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.entity.SysUser;

import java.util.List;

/**
 * 系统用户 feign
 *
 * @author 雨滴kian
 * @date 2022/6/28
 */
public interface RemoteSysUserService {

	/**
	 * 通过用户名查询用户、角色信息
	 *
	 * @author 雨滴kian
	 * @date 2022/6/28
	 * @param username
	 * @return: com.aryn.cloud.upms.common.entity.SysUser
	 */
	SysUser getUserInfo(String username);

	/**
	 * 通过手机号查询用户、角色信息
	 *
	 * @author 雨滴kian
	 * @date 2022/7/5
	 * @param phone
	 * @return: com.aryn.cloud.common.core.util.Result<com.aryn.cloud.upms.common.entity.SysUser>
	 */
	SysUser getUserInfoByPhone(String phone);

	/**
	 * 通过ID查询用户及权限标识（含角色菜单权限），租户隔离由调用链上下文保证
	 * @param id 用户ID
	 * @return 用户信息，不存在返回 null
	 */
	SysUser getUserById(String id);

	/**
	 * 批量查询用户及权限标识
	 * @param ids 用户ID列表
	 * @return 用户信息列表（不含不存在的用户）
	 */
	List<SysUser> getUserInfoByIds(List<String> ids);

	/**
	 * 按关键字搜索账号正常的员工（用户名/昵称/手机号模糊）
	 * @param keyword 关键字
	 * @param limit 最大返回数量
	 * @return 员工列表（不含权限标识）
	 */
	List<SysUser> searchNormalUsers(String keyword, int limit);

	/**
	 * 按角色编码授予或回收用户角色（用于配送资格开通/停用）
	 * @param userId 用户ID
	 * @param roleCode 角色编码
	 * @param grant true授予 false回收
	 * @return 是否成功
	 */
	boolean changeRoleByCode(String userId, String roleCode, boolean grant);

}
