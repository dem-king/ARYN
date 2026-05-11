package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.mapper.PointsConfigMapper;
import com.aryn.cloud.user.service.IPointsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 积分配置
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsConfigServiceImpl extends ServiceImpl<PointsConfigMapper, PointsConfig>
		implements IPointsConfigService {

	@Override
	public IPage<PointsConfig> getPage(Page page, PointsConfig pointsConfig) {
		return this.page(page,
				Wrappers.<PointsConfig>lambdaQuery().orderByDesc(PointsConfig::getCreateTime));
	}

	@Override
	public PointsConfig getDetailById(String id) {
		return this.getById(id);
	}

	@Override
	public boolean saveConfig(PointsConfig pointsConfig) {
		// 校验触发场景是否已存在启用的配置
		long count = this.count(Wrappers.<PointsConfig>lambdaQuery()
				.eq(PointsConfig::getTriggerScene, pointsConfig.getTriggerScene())
				.eq(PointsConfig::getRuleType, pointsConfig.getRuleType())
				.eq(PointsConfig::getStatus, "0"));
		if (count > 0) {
			throw new ArynBusinessException("相同触发场景和规则类型的启用配置已存在");
		}
		return this.save(pointsConfig);
	}

	@Override
	public boolean updateConfig(PointsConfig pointsConfig) {
		return this.updateById(pointsConfig);
	}

	@Override
	public boolean deleteConfig(String id) {
		return this.removeById(id);
	}

}
