package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.aryn.cloud.upms.api.remote.RemoteWechatBindingService;
import com.aryn.cloud.upms.service.ISysUserWechatBindingService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteWechatBindingServiceImpl implements RemoteWechatBindingService {

	private final ISysUserWechatBindingService wechatBindingService;

	@Override
	public String getOpenid(String tenantId, String userId) {
		SysUserWechatBinding binding = wechatBindingService.getActiveBinding(tenantId, userId);
		return binding == null ? null : binding.getOpenid();
	}
}