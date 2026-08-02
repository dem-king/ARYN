package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.aryn.cloud.upms.mapper.SysUserWechatBindingMapper;
import com.aryn.cloud.upms.service.ISysUserWechatBindingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SysUserWechatBindingServiceImpl
		extends ServiceImpl<SysUserWechatBindingMapper, SysUserWechatBinding>
		implements ISysUserWechatBindingService {

	@Override
	public SysUserWechatBinding getActiveBinding(String tenantId, String userId) {
		return getOne(new LambdaQueryWrapper<SysUserWechatBinding>()
				.eq(SysUserWechatBinding::getTenantId, tenantId)
				.eq(SysUserWechatBinding::getUserId, userId)
				.eq(SysUserWechatBinding::getStatus, "1")
				.eq(SysUserWechatBinding::getDelFlag, CommonConstants.NO)
				.last("LIMIT 1"));
	}

	@Override
	public void bindOrUpdate(String tenantId, String userId, String appId, String openid) {
		SysUserWechatBinding existing = getActiveBinding(tenantId, userId);
		LocalDateTime now = LocalDateTime.now();
		if (existing != null) {
			if (!appId.equals(existing.getAppId()) || !openid.equals(existing.getOpenid())) {
				existing.setAppId(appId);
				existing.setOpenid(openid);
				existing.setBindTime(now);
				updateById(existing);
			}
			return;
		}
		SysUserWechatBinding binding = new SysUserWechatBinding();
		binding.setTenantId(tenantId);
		binding.setUserId(userId);
		binding.setAppId(appId);
		binding.setOpenid(openid);
		binding.setStatus("1");
		binding.setBindTime(now);
		binding.setCreateBy(userId);
		binding.setCreateTime(now);
		binding.setDelFlag(CommonConstants.NO);
		save(binding);
	}
}