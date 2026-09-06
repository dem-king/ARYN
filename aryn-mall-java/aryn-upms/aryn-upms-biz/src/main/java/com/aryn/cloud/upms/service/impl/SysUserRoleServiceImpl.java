
package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户关联角色
 *
 * <p>回收角色使用逻辑删除（del_flag='1'）保留历史；
 * 再次授予时优先恢复同一条已删除关联，避免重复新增历史行。
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Slf4j
@Service
public class SysUserRoleServiceImpl extends ServiceImpl<SysUserRoleMapper, SysUserRole> implements ISysUserRoleService {

	/** 逻辑删除：显示 */
	private static final String DEL_FLAG_NORMAL = "0";

	@Override
	public boolean grantRole(String userId, String roleId) {
		SysUserRole existing = baseMapper.selectAnyByUserAndRole(userId, roleId);
		if (existing != null && DEL_FLAG_NORMAL.equals(existing.getDelFlag())) {
			return Boolean.TRUE;
		}
		if (existing != null) {
			baseMapper.reviveById(existing.getId());
			log.info("恢复用户角色关联：userId={}，roleId={}", userId, roleId);
			return Boolean.TRUE;
		}
		SysUserRole userRole = new SysUserRole();
		userRole.setUserId(userId);
		userRole.setRoleId(roleId);
		save(userRole);
		return Boolean.TRUE;
	}

}
