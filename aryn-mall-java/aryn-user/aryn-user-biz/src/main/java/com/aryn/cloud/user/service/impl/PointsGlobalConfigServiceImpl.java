package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.mapper.PointsGlobalConfigMapper;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 积分全局规则配置。
 *
 * <p>行业口径：有效期 365 天滚动（小象/山姆）、抵扣上限可配（京东分档 50%-90%、
 * 淘金币 3%/5%/10%）、门槛/封顶（京豆单笔>500 起、永辉单笔 5 元封顶）。
 * 默认值保持现网行为：20% 上限、无门槛、不封顶，有效期默认 365 天。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsGlobalConfigServiceImpl extends ServiceImpl<PointsGlobalConfigMapper, PointsGlobalConfig>
		implements IPointsGlobalConfigService {

	private static final int DEFAULT_VALIDITY_DAYS = 365;

	private static final BigDecimal DEFAULT_DEDUCT_MAX_RATIO = new BigDecimal("20.00");

	@Override
	public PointsGlobalConfig getEffectiveConfig() {
		PointsGlobalConfig config = this.getOne(Wrappers.<PointsGlobalConfig>lambdaQuery().last("LIMIT 1"));
		if (config != null) {
			return config;
		}
		return new PointsGlobalConfig().setValidityDays(DEFAULT_VALIDITY_DAYS)
			.setDeductMaxRatio(DEFAULT_DEDUCT_MAX_RATIO)
			.setMinUseAmount(BigDecimal.ZERO)
			.setMaxDeductAmount(BigDecimal.ZERO);
	}

	@Override
	public boolean saveConfig(PointsGlobalConfig config) {
		if (config == null) {
			throw new ArynBusinessException("积分全局配置不能为空");
		}
		validate(config);
		PointsGlobalConfig existing = this.getOne(Wrappers.<PointsGlobalConfig>lambdaQuery().last("LIMIT 1"));
		if (existing == null) {
			config.setId(null);
			return this.save(config);
		}
		config.setId(existing.getId());
		return this.updateById(config);
	}

	private void validate(PointsGlobalConfig config) {
		if (config.getValidityDays() == null || config.getValidityDays() < 0 || config.getValidityDays() > 3650) {
			throw new ArynBusinessException("积分有效期为 0-3650 天（0 表示永不过期）");
		}
		BigDecimal ratio = config.getDeductMaxRatio();
		if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0 || ratio.compareTo(new BigDecimal("100")) > 0) {
			throw new ArynBusinessException("单笔抵扣上限比例必须在 (0, 100] 之间");
		}
		if (isNegative(config.getMinUseAmount())) {
			throw new ArynBusinessException("最低使用门槛不能为负数");
		}
		if (isNegative(config.getMaxDeductAmount())) {
			throw new ArynBusinessException("单笔抵扣封顶金额不能为负数");
		}
	}

	private boolean isNegative(BigDecimal value) {
		return value != null && value.compareTo(BigDecimal.ZERO) < 0;
	}

}
