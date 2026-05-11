package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.RechargeConfig;
import com.aryn.cloud.user.mapper.RechargeConfigMapper;
import com.aryn.cloud.user.service.IRechargeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 储值配置
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeConfigServiceImpl extends ServiceImpl<RechargeConfigMapper, RechargeConfig>
		implements IRechargeConfigService {

	@Override
	public IPage<RechargeConfig> getPage(Page page, RechargeConfig rechargeConfig) {
		return this.page(page,
				Wrappers.<RechargeConfig>lambdaQuery().orderByAsc(RechargeConfig::getSortOrder).orderByDesc(RechargeConfig::getCreateTime));
	}

	@Override
	public RechargeConfig getDetailById(String id) {
		return this.getById(id);
	}

	@Override
	public boolean saveConfig(RechargeConfig rechargeConfig) {
		if (rechargeConfig.getRechargeAmount() == null
				|| rechargeConfig.getRechargeAmount().doubleValue() <= 0) {
			throw new ArynBusinessException("充值金额必须大于0");
		}
		return this.save(rechargeConfig);
	}

	@Override
	public boolean updateConfig(RechargeConfig rechargeConfig) {
		if (rechargeConfig.getRechargeAmount() == null
				|| rechargeConfig.getRechargeAmount().doubleValue() <= 0) {
			throw new ArynBusinessException("充值金额必须大于0");
		}
		return this.updateById(rechargeConfig);
	}

	@Override
	public boolean deleteConfig(String id) {
		return this.removeById(id);
	}

}
