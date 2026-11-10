package com.aryn.cloud.upms.service;

import com.aryn.cloud.upms.api.dto.CopyrightConfigDTO;
import com.aryn.cloud.upms.api.vo.AppCopyrightConfigVO;
import com.aryn.cloud.upms.api.vo.CopyrightConfigVO;

/**
 * 版权配置。
 *
 * <p>权限口径：用户端（app）由各租户自己维护；商户端与平台端属平台级配置，
 * 仅平台管理员可读写（守卫在 Controller 层，与租户管理同口径）。</p>
 */
public interface ISysCopyrightConfigService {

	/**
	 * 读取指定租户、指定端的原始配置；未配置过时各项为空串（不返回 null，前端表单可直接绑定）。
	 */
	CopyrightConfigVO getConfig(String tenantId, String clientType);

	/**
	 * 保存指定租户、指定端的配置（按端 upsert），并失效该端的全部渲染缓存。
	 */
	void saveConfig(String tenantId, CopyrightConfigDTO dto);

	/**
	 * 渲染端下发值：名称/Logo 留空时回落该租户的品牌（sys_tenant.name/logoUrl）。
	 */
	AppCopyrightConfigVO getRenderConfig(String tenantId, String clientType);

	/**
	 * 平台端登录页配置：登录前无会话，固定取平台租户（tenant_type='1'）的配置。
	 */
	AppCopyrightConfigVO getPlatformLoginConfig();

	/**
	 * 租户名称/Logo 变更后失效该租户全部端的下发缓存（名称与 Logo 留空时跟随租户品牌）。
	 */
	void evictTenantRenderCache(String tenantId);

}