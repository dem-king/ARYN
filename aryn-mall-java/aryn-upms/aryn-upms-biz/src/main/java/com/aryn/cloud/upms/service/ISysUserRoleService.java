
package com.aryn.cloud.upms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.upms.api.entity.SysUserRole;

/**
 * 用户关联角色
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
public interface ISysUserRoleService extends IService<SysUserRole> {

	/**
	 * 授予用户角色（幂等）：有效关联直接返回；已逻辑删除的关联恢复原行；否则新增
	 * @param userId 用户ID
	 * @param roleId 角色ID
	 * @return 是否生效（已拥有或本次授予成功）
	 */
	boolean grantRole(String userId, String roleId);

}
