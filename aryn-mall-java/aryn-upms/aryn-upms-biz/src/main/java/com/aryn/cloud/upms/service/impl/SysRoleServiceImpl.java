
package com.aryn.cloud.upms.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.service.ISysRoleService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 角色
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:47
 */
@Service
@AllArgsConstructor
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements ISysRoleService {

	@Override
	public boolean checkRole(SysRole sysRole) {
		LambdaQueryWrapper<SysRole> lqw = Wrappers.lambdaQuery();
		if (StrUtil.isNotBlank(sysRole.getId())) {
			lqw.ne(SysRole::getId, sysRole.getId());
		}
		if (StrUtil.isNotBlank(sysRole.getRoleCode())) {
			lqw.eq(SysRole::getRoleCode, sysRole.getRoleCode());
		}
		if (StrUtil.isNotBlank(sysRole.getRoleName())) {
			lqw.eq(SysRole::getRoleName, sysRole.getRoleName());
		}
		return baseMapper.selectCount(lqw) > 0;
	}

	@Override
	public List<String> findRoleIdsByUserId(String userId) {
		return baseMapper.listRoleIdsByUserId(userId);
	}

	@Override
	public IPage<SysRole> getPage(Page page, SysRole sysRole) {
		return baseMapper.selectRolePage(page, sysRole);
	}

}
