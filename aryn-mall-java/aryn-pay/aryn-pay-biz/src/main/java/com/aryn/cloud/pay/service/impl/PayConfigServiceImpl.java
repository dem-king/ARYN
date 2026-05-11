
package com.aryn.cloud.pay.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import org.springframework.stereotype.Service;

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
		long count = this.count(Wrappers.<PayConfig>lambdaQuery()
			.eq(PayConfig::getType, payConfig.getType())
			.eq(PayConfig::getTerminalType, payConfig.getTerminalType()));
		if (count > 0) {
			throw new RuntimeException("该支付方式已存在");
		}
		return this.save(payConfig);
	}

}
