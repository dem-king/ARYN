
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.vo.SysUserVO;

/**
 * 系统用户
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysUserService extends IService<SysUser> {

	/**
	 * 查询用户信息
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: com.aryn.cloud.upms.common.entity.SysUser
	 */

	SysUser findUserInfo(SysUser sysUser);

	/**
	 * 用户名查询用户信息
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param username
	 * @return: com.aryn.cloud.upms.common.entity.SysUser
	 */
	SysUser findUserByName(String username);

	/**
	 * 手机号查询用户信息
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param phone
	 * @return: com.aryn.cloud.upms.common.entity.SysUser
	 */
	SysUser findUserByPhone(String phone);

	/**
	 * 新增用户
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: boolean
	 */
	boolean saveUser(SysUser sysUser);

	/**
	 * 修改用户
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param sysUser
	 * @return: boolean
	 */
	boolean updateUser(SysUser sysUser);

	/**
	 * 用户列表查询（返回不含密码的响应 VO）
	 *
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @param page
	 * @param sysUser
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.upms.api.vo.SysUserVO>
	 */
	IPage<SysUserVO> adminPage(Page page, SysUser sysUser);

	/**
	 * 删除用户
	 *
	 * @author 雨滴kian
	 * @date 2022/7/19
	 * @param sysUser
	 * @return: boolean
	 */
	boolean delUser(SysUser sysUser);

	/**
	 * 按 ID 查询对当前会话可见的账号。
	 *
	 * <p>平台管理员账号是系统内置账号，不在员工账号页面暴露：列表查询已过滤，
	 * 详情/手机号等按 ID 读取的入口也必须走本方法，避免通过已知 ID 读到其资料。</p>
	 *
	 * @param userId 目标账号 ID
	 * @return 可见账号；不存在或对当前会话不可见时返回 {@code null}
	 */
	SysUser getVisibleUserById(String userId);

	/**
	 * 当前租户管理端员工账号数（排除平台管理员账号）。
	 *
	 * <p>平台管理员是系统级身份、不属于任何租户，租户概览等统计不能把它算作租户员工。</p>
	 */
	long countVisibleStaff();

	int getCount(SysUser sysUser);

}
