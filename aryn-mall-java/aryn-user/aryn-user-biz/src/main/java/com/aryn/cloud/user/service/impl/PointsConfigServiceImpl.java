package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.mapper.PointsConfigMapper;
import com.aryn.cloud.user.service.IPointsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
		normalize(pointsConfig);
		validate(pointsConfig, null);
		return this.save(pointsConfig);
	}

	@Override
	public boolean updateConfig(PointsConfig pointsConfig) {
		if (pointsConfig == null || !StringUtils.hasText(pointsConfig.getId())) {
			throw new ArynBusinessException("积分配置ID不能为空");
		}
		normalize(pointsConfig);
		validate(pointsConfig, pointsConfig.getId());
		return this.updateById(pointsConfig);
	}

	@Override
	public boolean deleteConfig(String id) {
		return this.removeById(id);
	}

	private void normalize(PointsConfig pointsConfig) {
		if (pointsConfig == null) {
			throw new ArynBusinessException("积分配置不能为空");
		}
		if (!StringUtils.hasText(pointsConfig.getStatus())) {
			pointsConfig.setStatus("0");
		}
	}

	private void validate(PointsConfig pointsConfig, String excludeId) {
		if (!StringUtils.hasText(pointsConfig.getRuleName())) {
			throw new ArynBusinessException("规则名称不能为空");
		}
		PointsSceneEnum scene = PointsSceneEnum.fromCode(pointsConfig.getTriggerScene());
		if (scene == null || !scene.isConfigurable()) {
			throw new ArynBusinessException("触发场景不合法");
		}
		if (!"1".equals(pointsConfig.getRuleType()) && !"2".equals(pointsConfig.getRuleType())) {
			throw new ArynBusinessException("规则类型不合法");
		}
		if (!scene.getChangeType().equals(pointsConfig.getRuleType())) {
			throw new ArynBusinessException(scene.getLabel() + "只能配置为"
					+ ("1".equals(scene.getChangeType()) ? "获取" : "消耗") + "规则");
		}
		if (pointsConfig.getPointValue() == null || pointsConfig.getPointValue() <= 0) {
			throw new ArynBusinessException("积分值必须大于0");
		}
		if (!"0".equals(pointsConfig.getStatus())) {
			return;
		}
		long count = this.count(Wrappers.<PointsConfig>lambdaQuery()
			.eq(PointsConfig::getTriggerScene, pointsConfig.getTriggerScene())
			.eq(PointsConfig::getRuleType, pointsConfig.getRuleType())
			.eq(PointsConfig::getStatus, "0")
			.ne(StringUtils.hasText(excludeId), PointsConfig::getId, excludeId));
		if (count > 0) {
			throw new ArynBusinessException("相同触发场景和规则类型的启用配置已存在");
		}
	}

}
