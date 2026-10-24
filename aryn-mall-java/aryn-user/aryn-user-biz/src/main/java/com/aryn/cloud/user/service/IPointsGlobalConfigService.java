package com.aryn.cloud.user.service;

import com.aryn.cloud.user.api.entity.PointsGlobalConfig;

/**
 * 积分全局规则配置：有效期 / 抵扣上限比例 / 最低使用门槛 / 单笔封顶。
 */
public interface IPointsGlobalConfigService {

	/**
	 * 读取当前租户生效配置；未维护过时返回默认值（365 天 / 20% / 无门槛 / 不封顶），不落库。
	 */
	PointsGlobalConfig getEffectiveConfig();

	/**
	 * 保存（每租户一行，存在即更新）。
	 */
	boolean saveConfig(PointsGlobalConfig config);

}
