package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.PointsConfig;

/**
 * 积分配置
 *
 * @author 雨滴kian
 */
public interface IPointsConfigService extends IService<PointsConfig> {

	IPage<PointsConfig> getPage(Page page, PointsConfig pointsConfig);

	PointsConfig getDetailById(String id);

	boolean saveConfig(PointsConfig pointsConfig);

	boolean updateConfig(PointsConfig pointsConfig);

	boolean deleteConfig(String id);

}
