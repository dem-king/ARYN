package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesConfigSaveDTO;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.mapper.SalesConfigMapper;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 销售业绩全局配置服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesConfigServiceImpl extends ServiceImpl<SalesConfigMapper, SalesConfig> implements ISalesConfigService {

	/** 全局默认提成比例 5% */
	public static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0500");

	/** 转交奖励默认不启用 */
	public static final BigDecimal DEFAULT_REFERRAL_REWARD_RATE = BigDecimal.ZERO;

	/** 每月台账生成日默认 1 日 */
	public static final int DEFAULT_MONTHLY_CUTOFF_DAY = 1;

	/** 退款风险观察期默认 7 天 */
	public static final int DEFAULT_REFUND_RISK_DAYS = 7;

	/** 认领有效期默认 90 天 */
	public static final int DEFAULT_CLAIM_EXPIRE_DAYS = 90;

	private static final String STATUS_ENABLE = "0";

	private static final String STATUS_DISABLE = "1";

	@Override
	public SalesConfig getActiveConfigOrDefaults() {
		SalesConfig config = this.getOne(Wrappers.<SalesConfig>lambdaQuery()
			.eq(SalesConfig::getStatus, STATUS_ENABLE)
			.last("limit 1"));
		if (config == null) {
			config = defaultConfig();
		}
		else {
			normalize(config);
		}
		return config;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean saveConfig(SalesConfigSaveDTO dto) {
		if (dto == null) {
			throw new ArynBusinessException("配置不能为空");
		}
		SalesConfig config = new SalesConfig();
		BeanUtils.copyProperties(dto, config);
		normalize(config);
		validate(config);
		SalesConfig exists = this.getOne(Wrappers.<SalesConfig>lambdaQuery()
			.eq(SalesConfig::getStatus, STATUS_ENABLE)
			.last("limit 1"));
		config.setStatus(STATUS_ENABLE);
		if (exists == null) {
			log.info("新增销售业绩全局配置");
			return this.save(config);
		}
		config.setId(exists.getId());
		log.info("更新销售业绩全局配置 configId={}", exists.getId());
		return this.updateById(config);
	}

	/**
	 * 未配置时的兜底默认值（不落库）。
	 */
	private SalesConfig defaultConfig() {
		return new SalesConfig()
			.setDefaultCommissionRate(DEFAULT_COMMISSION_RATE)
			.setReferralRewardRate(DEFAULT_REFERRAL_REWARD_RATE)
			.setMonthlyCutoffDay(DEFAULT_MONTHLY_CUTOFF_DAY)
			.setRefundRiskDays(DEFAULT_REFUND_RISK_DAYS)
			.setClaimExpireDays(DEFAULT_CLAIM_EXPIRE_DAYS)
			.setStatus(STATUS_ENABLE);
	}

	private void normalize(SalesConfig config) {
		if (config.getDefaultCommissionRate() == null) {
			config.setDefaultCommissionRate(DEFAULT_COMMISSION_RATE);
		}
		if (config.getReferralRewardRate() == null) {
			config.setReferralRewardRate(DEFAULT_REFERRAL_REWARD_RATE);
		}
		if (config.getMonthlyCutoffDay() == null) {
			config.setMonthlyCutoffDay(DEFAULT_MONTHLY_CUTOFF_DAY);
		}
		if (config.getRefundRiskDays() == null) {
			config.setRefundRiskDays(DEFAULT_REFUND_RISK_DAYS);
		}
		if (config.getClaimExpireDays() == null) {
			config.setClaimExpireDays(DEFAULT_CLAIM_EXPIRE_DAYS);
		}
		if (config.getStatus() == null) {
			config.setStatus(STATUS_ENABLE);
		}
	}

	private void validate(SalesConfig config) {
		BigDecimal rate = config.getDefaultCommissionRate();
		if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("默认提成比例必须在0到1之间");
		}
		BigDecimal referral = config.getReferralRewardRate();
		if (referral.signum() < 0 || referral.compareTo(BigDecimal.ONE) > 0) {
			throw new ArynBusinessException("转交奖励比例必须在0到1之间");
		}
		int cutoff = config.getMonthlyCutoffDay();
		if (cutoff < 1 || cutoff > 28) {
			throw new ArynBusinessException("每月台账生成日必须在1到28之间");
		}
		int riskDays = config.getRefundRiskDays();
		if (riskDays < 0 || riskDays > 365) {
			throw new ArynBusinessException("退款风险观察期必须在0到365天之间");
		}
		int claimDays = config.getClaimExpireDays();
		if (claimDays < 1 || claimDays > 365) {
			throw new ArynBusinessException("认领有效期必须在1到365天之间");
		}
		if (STATUS_DISABLE.equals(config.getStatus())) {
			throw new ArynBusinessException("销售配置不支持禁用");
		}
	}

}
