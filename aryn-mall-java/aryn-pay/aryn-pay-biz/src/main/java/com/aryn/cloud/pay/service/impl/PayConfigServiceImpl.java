
package com.aryn.cloud.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付配置
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Service
public class PayConfigServiceImpl extends ServiceImpl<PayConfigMapper, PayConfig> implements IPayConfigService {

	@Override
	public PayConfig getByAppId(String appId) {
		return baseMapper.selectByAppId(appId);
	}

	@Override
	public PayConfigVO getConfig(String payType, String terminalType) {
		return baseMapper.selectConfig(payType, terminalType);
	}

	@Override
	public boolean saveConfig(PayConfig payConfig) {
		validateConfig(payConfig);
		assertUnique(payConfig.getType(), payConfig.getTerminalType(), null);
		return this.save(payConfig);
	}

	@Override
	public boolean updateConfig(PayConfig payConfig) {
		validateConfig(payConfig);
		assertUnique(payConfig.getType(), payConfig.getTerminalType(), payConfig.getId());
		return this.updateById(payConfig);
	}

	private void validateConfig(PayConfig payConfig) {
		if (!StringUtils.hasText(payConfig.getType()) || !StringUtils.hasText(payConfig.getTerminalType())) {
			throw new ArynBusinessException("支付类型和支付端类型不能为空");
		}
		if (!StringUtils.hasText(payConfig.getAppId()) || !StringUtils.hasText(payConfig.getMchId())) {
			throw new ArynBusinessException("应用ID和商户号不能为空");
		}
	}

	private void assertUnique(String type, String terminalType, String excludeId) {
		LambdaQueryWrapper<PayConfig> query = Wrappers.<PayConfig>lambdaQuery()
			.eq(PayConfig::getType, type)
			.eq(PayConfig::getTerminalType, terminalType);
		if (StringUtils.hasText(excludeId)) {
			query.ne(PayConfig::getId, excludeId);
		}
		if (this.count(query) > 0) {
			throw new ArynBusinessException("该支付方式已存在");
		}
	}

}
