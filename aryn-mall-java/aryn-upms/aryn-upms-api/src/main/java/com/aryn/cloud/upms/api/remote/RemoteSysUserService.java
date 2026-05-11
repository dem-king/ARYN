
package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.entity.SysUser;

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

}
