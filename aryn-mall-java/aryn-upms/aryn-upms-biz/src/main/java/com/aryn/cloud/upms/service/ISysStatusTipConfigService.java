package com.aryn.cloud.upms.service;

import com.aryn.cloud.upms.api.dto.StatusTipConfigDTO;
import com.aryn.cloud.upms.api.vo.StatusTipConfigVO;

/**
 * 空态插画配置。
 */
public interface ISysStatusTipConfigService {

	/**
	 * 管理端读取当前租户配置（未配置时返回 7 个空串）。
	 */
	StatusTipConfigVO getConfig(String tenantId);

	/**
	 * 保存当前租户配置，全字段覆盖；空串表示清除并回落内置静态图。
	 */
	void saveConfig(String tenantId, StatusTipConfigDTO dto);

	/**
	 * C 端免登下发：只返回租户已配置的值，空串由前端回落到内置静态图。
	 */
	StatusTipConfigVO getRenderConfig(String tenantId);

}
