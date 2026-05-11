/** 
  * Copyright (c) 2025 天启雨数科技有限公司 
  * All rights reserved. 
  * <p> 
  * 注意： 
  * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。 
 
  */
package com.aryn.cloud.pay.config;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.aryn.cloud.common.core.util.SpringUtils;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.service.IPayConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付自动配置
 *
 * @author 雨滴kian
 * @since 2022/3/14 15:24
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(WxPayService.class)
public class WxPayConfiguration {

	/**
	 * 构造微信支付服务对象.
	 * @return 微信支付service
	 */
	public static WxPayService wxPayService(String terminalType) {
		IPayConfigService payConfigService = SpringUtils.getBean(IPayConfigService.class);

		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_1, terminalType);
		if (null == payConfig) {
			throw new IllegalArgumentException("未配置微信支付");
		}
		final WxPayServiceImpl wxPayService = new WxPayServiceImpl();
		WxPayConfig wxPayConfig = new WxPayConfig();
		wxPayConfig.setAppId(payConfig.getAppId());
		wxPayConfig.setMchId(payConfig.getMchId());
		wxPayConfig.setMchKey(payConfig.getMchKey());
		wxPayConfig.setKeyPath(payConfig.getKeyPath());
		wxPayConfig.setCertSerialNo(payConfig.getCertSerialNo());

		// 以下是apiv3
		wxPayConfig.setPrivateKeyPath(payConfig.getPrivateKeyPath());
		wxPayConfig.setPrivateCertPath(payConfig.getPrivateCertPath());
		wxPayConfig.setPublicKeyId(payConfig.getPublicKeyId());
		wxPayConfig.setPublicKeyPath(payConfig.getPublicKeyPath());
		wxPayConfig.setApiV3Key(payConfig.getApiv3Key());
		wxPayConfig.setFullPublicKeyModel(true);
		wxPayService.setConfig(wxPayConfig);
		return wxPayService;
	}

}
