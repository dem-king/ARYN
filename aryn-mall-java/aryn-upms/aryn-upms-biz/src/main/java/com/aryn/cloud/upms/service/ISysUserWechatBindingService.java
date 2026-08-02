package com.aryn.cloud.upms.service;

import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISysUserWechatBindingService extends IService<SysUserWechatBinding> {

	/**
	 * 根据员工ID获取有效绑定。
	 */
	SysUserWechatBinding getActiveBinding(String tenantId, String userId);

	/**
	 * 绑定或更新员工微信 openid。
	 */
	void bindOrUpdate(String tenantId, String userId, String appId, String openid);
}