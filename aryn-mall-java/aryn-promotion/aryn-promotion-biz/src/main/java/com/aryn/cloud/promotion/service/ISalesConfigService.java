package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SalesConfigSaveDTO;
import com.aryn.cloud.promotion.api.entity.SalesConfig;

/**
 * 销售业绩全局配置服务（每租户单启用的配置行；未配置时返回默认值）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesConfigService extends IService<SalesConfig> {

	/**
	 * 读取当前启用配置；不存在时返回带默认值的对象（不落库）
	 * @return 配置（非 null）
	 */
	SalesConfig getActiveConfigOrDefaults();

	/**
	 * 保存配置（存在则更新，不存在则新增启用行；每租户单启用）
	 * @param dto 配置保存请求
	 * @return 是否成功
	 */
	boolean saveConfig(SalesConfigSaveDTO dto);

}
