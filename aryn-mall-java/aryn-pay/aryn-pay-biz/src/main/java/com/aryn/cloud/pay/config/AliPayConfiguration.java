package com.aryn.cloud.pay.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.aryn.cloud.common.core.util.SpringUtils;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.service.IPayConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(AliPayConfiguration.class)
public class AliPayConfiguration {

	private static final String DEFAULT_ALIPAY_SERVER_URL = "https://openapi.alipay.com/gateway.do";

	public static AlipayClient getAlipayClient(String terminalType) throws Exception {
		IPayConfigService payConfigService = SpringUtils.getBean(IPayConfigService.class);

		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_2, terminalType);
		if (null == payConfig) {
			throw new IllegalArgumentException("未配置支付宝支付");
		}

		String serverUrl = SpringUtils.getBean(Environment.class)
			.getProperty("alipay.server-url", DEFAULT_ALIPAY_SERVER_URL);

		CertAlipayRequest certAlipayRequest = new CertAlipayRequest();
		certAlipayRequest.setServerUrl(serverUrl);
		certAlipayRequest.setAppId(payConfig.getAppId());
		certAlipayRequest.setPrivateKey(payConfig.getMchKey());
		certAlipayRequest.setFormat("json");
		certAlipayRequest.setCharset("utf-8");
		certAlipayRequest.setSignType("RSA2");
		certAlipayRequest.setCertPath(payConfig.getPrivateCertPath());
		certAlipayRequest.setAlipayPublicCertPath(payConfig.getPrivateKeyPath());
		certAlipayRequest.setRootCertPath(payConfig.getKeyPath());
		return new DefaultAlipayClient(certAlipayRequest);
	}

}
