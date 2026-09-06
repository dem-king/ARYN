
package com.aryn.cloud.upms.dubbo;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/22
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSysUserServiceImpl implements RemoteSysUserService {

	private final ISysUserService sysUserService;

	private final SysRoleMapper sysRoleMapper;

	private final SysUserRoleMapper sysUserRoleMapper;

	private final ISysUserRoleService sysUserRoleService;

	@Override
	public SysUser getUserInfo(String username) {
		// 首先通过用户名获取用户信息
		SysUser sysUser = sysUserService.findUserByName(username);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		// 从用户信息中获取租户ID，设置到线程变量中
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public SysUser getUserInfoByPhone(String phone) {
		// 首先通过手机号获取用户信息
		SysUser sysUser = sysUserService.findUserByPhone(phone);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		// 从用户信息中获取租户ID，设置到线程变量中
		ArynTenantContextHolder.setTenantId(sysUser.getTenantId());
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public SysUser getUserById(String id) {
		if (StrUtil.isBlank(id)) {
			return null;
		}
		SysUser sysUser = sysUserService.getById(id);
		if (ObjectUtil.isNull(sysUser)) {
			return null;
		}
		return sysUserService.findUserInfo(sysUser);
	}

	@Override
	public List<SysUser> getUserInfoByIds(List<String> ids) {
		if (CollUtil.isEmpty(ids)) {
			return Collections.emptyList();
		}
		List<SysUser> users = sysUserService.listByIds(ids);
		users.forEach(sysUserService::findUserInfo);
		return users;
	}

	@Override
	public List<SysUser> searchNormalUsers(String keyword, int limit) {
		int safeLimit = Math.min(Math.max(limit, 1), 50);
		return sysUserService.list(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getStatus, CommonConstants.NORMAL_STATUS)
			.and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
				.like(SysUser::getUsername, keyword)
				.or()
				.like(SysUser::getNickname, keyword)
				.or()
				.like(SysUser::getPhone, keyword))
			.last("LIMIT " + safeLimit));
	}

	@Override
	public boolean changeRoleByCode(String userId, String roleCode, boolean grant) {
		if (StrUtil.hasBlank(userId, roleCode)) {
			throw new ArynBusinessException("用户ID和角色编码不能为空");
		}
		SysRole role = sysRoleMapper
			.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
		if (ObjectUtil.isNull(role)) {
			throw new ArynBusinessException("未找到编码为 " + roleCode + " 的角色，请先在角色管理中创建");
		}
		if (grant) {
			// 授予幂等：已有有效关联直接返回；已逻辑删除的关联恢复原行；否则新增
			sysUserRoleService.grantRole(userId, role.getId());
			log.info("配送资格开通：用户 {} 授予角色 {}（{}）", userId, role.getRoleName(), roleCode);
		}
		else {
			// 回收幂等：逻辑删除保留历史，权限计算过滤已删除关联；
			// @TableLogic 使 selectCount/delete 自动携带 del_flag='0' 条件
			Long exists = sysUserRoleMapper
				.selectCount(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId)
					.eq(SysUserRole::getRoleId, role.getId()));
			if (exists == 0) {
				return Boolean.TRUE;
			}
			sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId)
				.eq(SysUserRole::getRoleId, role.getId()));
			log.info("配送资格停用：用户 {} 回收角色 {}（{}）", userId, role.getRoleName(), roleCode);
		}
		return Boolean.TRUE;
	}

}
