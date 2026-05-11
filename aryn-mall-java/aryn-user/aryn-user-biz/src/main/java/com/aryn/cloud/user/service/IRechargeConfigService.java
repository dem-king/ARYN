package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.RechargeConfig;

/**
 * 储值配置
 *
 * @author 雨滴kian
 */
public interface IRechargeConfigService extends IService<RechargeConfig> {

	IPage<RechargeConfig> getPage(Page page, RechargeConfig rechargeConfig);

	RechargeConfig getDetailById(String id);

	boolean saveConfig(RechargeConfig rechargeConfig);

	boolean updateConfig(RechargeConfig rechargeConfig);

	boolean deleteConfig(String id);

}
