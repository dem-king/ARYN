package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;

/**
 * 分销配置服务
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
public interface IDistributionConfigService extends IService<DistributionConfig> {

	/**
	 * 获取当前启用的分销配置
	 *
	 * @return 启用的分销配置，不存在则返回null
	 */
	DistributionConfig getActiveConfig();

	/**
	 * 启用指定配置（同时禁用其他所有配置）
	 *
	 * @param configId 配置ID
	 * @return 是否成功
	 */
	Boolean enableConfig(String configId);

}
