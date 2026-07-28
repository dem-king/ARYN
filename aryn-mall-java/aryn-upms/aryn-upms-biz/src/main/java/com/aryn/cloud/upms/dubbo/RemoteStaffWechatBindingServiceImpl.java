package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.aryn.cloud.upms.api.remote.RemoteStaffWechatBindingService;
import com.aryn.cloud.upms.mapper.SysUserWechatBindingMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/** 配送员工微信绑定实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteStaffWechatBindingServiceImpl implements RemoteStaffWechatBindingService {

	private final SysUserWechatBindingMapper bindingMapper;

	@Override
	public SysUserWechatBinding bind(String tenantId, String userId, String appId, String openId) {
		requireTenant(tenantId);
		if (!StringUtils.hasText(userId) || !StringUtils.hasText(appId) || !StringUtils.hasText(openId)) {
			throw new ArynBusinessException("员工微信绑定参数不完整");
		}
		LocalDateTime now = LocalDateTime.now();
		SysUserWechatBinding binding = new SysUserWechatBinding()
			.setId(IdWorker.getIdStr())
			.setUserId(userId)
			.setAppId(appId)
			.setOpenid(openId)
			.setStatus("BOUND")
			.setBoundAt(now)
			.setTenantId(tenantId)
			.setCreateBy(userId)
			.setCreateTime(now)
			.setDelFlag(CommonConstants.NO);
		bindingMapper.upsertBinding(binding);
		return binding;
	}

	@Override
	public String getBoundOpenId(String tenantId, String userId, String appId) {
		requireTenant(tenantId);
		if (!StringUtils.hasText(userId) || !StringUtils.hasText(appId)) {
			return null;
		}
		return bindingMapper.selectBoundOpenId(tenantId, userId, appId);
	}

	private void requireTenant(String tenantId) {
		if (!StringUtils.hasText(tenantId)
				|| !Objects.equals(tenantId, ArynTenantContextHolder.getTenantId())) {
			throw new ArynBusinessException("员工微信绑定租户不匹配");
		}
	}
}
