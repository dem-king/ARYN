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

import java.math.BigDecimal;
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

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean save(DistributionConfig entity) {
		validateConfig(entity);
		if (MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE.equals(entity.getStatus())) {
			disableOtherActiveConfigs(entity.getId());
		}
		return super.save(entity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean updateById(DistributionConfig entity) {
		validateConfig(entity);
		if (MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE.equals(entity.getStatus())) {
			disableOtherActiveConfigs(entity.getId());
		}
		return super.updateById(entity);
	}

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

		target.setStatus(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE);
		boolean result = updateById(target);

		log.info("分销配置切换启用 configId={}", configId);
		return result;
	}

	private void disableOtherActiveConfigs(String targetId) {
		List<DistributionConfig> enabledList = this.list(Wrappers.<DistributionConfig>lambdaQuery()
			.eq(DistributionConfig::getStatus, MallEventConstants.DISTRIBUTION_CONFIG_STATUS_ENABLE));
		for (DistributionConfig config : enabledList) {
			if (targetId == null || !targetId.equals(config.getId())) {
				config.setStatus(MallEventConstants.DISTRIBUTION_CONFIG_STATUS_DISABLE);
				if (!super.updateById(config)) {
					throw new ArynBusinessException("禁用旧分销配置失败，请重试");
				}
			}
		}
	}

	private void validateConfig(DistributionConfig config) {
		if (config == null || config.getCommissionRate() == null || config.getMinWithdrawAmount() == null) {
			throw new ArynBusinessException("分销配置参数不完整");
		}
		BigDecimal level1 = config.getCommissionRate();
		BigDecimal level2 = config.getCommissionRateLevel2() == null
			? BigDecimal.ZERO : config.getCommissionRateLevel2();
		if (level1.signum() < 0 || level1.compareTo(BigDecimal.ONE) > 0
				|| level2.signum() < 0 || level2.compareTo(BigDecimal.ONE) > 0
				|| level1.add(level2).compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("佣金比例必须在0到1之间且两级合计不能超过1");
		}
		if (config.getMinWithdrawAmount().signum() < 0) {
			throw new ArynBusinessException("最低提现金额不能小于0");
		}
		int cycleDays = config.getSettleCycleDays() == null ? 7 : config.getSettleCycleDays();
		if (cycleDays < 0 || cycleDays > 365) {
			throw new ArynBusinessException("结算周期必须在0到365天之间");
		}
	}

}
