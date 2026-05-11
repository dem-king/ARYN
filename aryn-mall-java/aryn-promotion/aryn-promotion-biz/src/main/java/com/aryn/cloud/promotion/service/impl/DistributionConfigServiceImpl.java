package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
import com.aryn.cloud.promotion.mapper.DistributionConfigMapper;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 分销配置服务实现
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionConfigServiceImpl extends ServiceImpl<DistributionConfigMapper, DistributionConfig>
	implements IDistributionConfigService {

	/**
	 * 获取当前启用的分销配置
	 *
	 * @return 启用的分销配置，不存在则返回null
	 */
	@Override
	public DistributionConfig getActiveConfig() {
		return this.getOne(Wrappers.<DistributionConfig>lambdaQuery()
			.eq(DistributionConfig::getStatus, MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE)
			.last("limit 1"));
	}

	/**
	 * 启用指定配置（同时禁用其他所有配置）
	 * <p>
	 * 保证全局只有一个启用的分销配置
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean enableConfig(String configId) {
		DistributionConfig target = this.getById(configId);
		if (target == null) {
			throw new ArynBusinessException("配置不存在");
		}

		// 1. 禁用所有当前启用的配置
		List<DistributionConfig> enabledList = this.list(Wrappers.<DistributionConfig>lambdaQuery()
			.eq(DistributionConfig::getStatus, MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE));
		for (DistributionConfig config : enabledList) {
			if (!config.getId().equals(configId)) {
				config.setStatus(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_DISABLE);
				this.updateById(config);
			}
		}

		// 2. 启用目标配置
		target.setStatus(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE);
		boolean result = this.updateById(target);

		log.info("分销配置切换启用 configId={}", configId);
		return result;
	}

}
