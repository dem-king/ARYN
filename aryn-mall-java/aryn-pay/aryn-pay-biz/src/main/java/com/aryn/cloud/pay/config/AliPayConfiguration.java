package com.aryn.cloud.pay.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.CertAlipayRequest;
import com.alipay.api.DefaultAlipayClient;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 支付宝证书模式。三类证书与应用私钥均从解密后的内存内容构造，不读取路径。
 */
@Component
@RequiredArgsConstructor
public class AliPayConfiguration {

	private final PayAlipayProperties properties;

	public AlipayClient create(PayResolvedIdentity identity) {
		try {
			return new DefaultAlipayClient(buildRequest(identity));
		}
		catch (Exception exception) {
			throw new ArynBusinessException("支付宝支付客户端初始化失败");
		}
	}

	/**
	 * 供检查/测试读取证书内容配置，不暴露到接口层。
	 */
	public CertAlipayRequest buildRequest(PayResolvedIdentity identity) {
		String privateKey = identity.materialText(PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY);
		String appCert = identity.materialText(PayConfigMaterialPurposeEnum.ALI_APP_CERT);
		String alipayPublicCert = identity.materialText(PayConfigMaterialPurposeEnum.ALI_PUBLIC_CERT);
		String rootCert = identity.materialText(PayConfigMaterialPurposeEnum.ALI_ROOT_CERT);
		if (!StringUtils.hasText(privateKey) || !StringUtils.hasText(appCert)
				|| !StringUtils.hasText(alipayPublicCert) || !StringUtils.hasText(rootCert)) {
			throw new ArynBusinessException("支付宝支付材料不完整");
		}
		CertAlipayRequest request = new CertAlipayRequest();
		request.setServerUrl(serverUrl(identity.getEnvironment()));
		request.setAppId(identity.getAppId());
		request.setPrivateKey(stripPem(privateKey));
		request.setFormat("json");
		request.setCharset("utf-8");
		request.setSignType("RSA2");
		request.setCertContent(appCert);
		request.setAlipayPublicCertContent(alipayPublicCert);
		request.setRootCertContent(rootCert);
		return request;
	}

	private String serverUrl(String environment) {
		if ("sandbox".equals(environment)) {
			return properties.getGatewaySandbox();
		}
		return properties.getGatewayProd();
	}

	/**
	 * 支付宝 SDK 的 privateKey 参数历史上接收纯 Base64，这里兼容 PEM 输入。
	 */
	private String stripPem(String value) {
		String trimmed = value.trim();
		if (!trimmed.contains("-----BEGIN")) {
			return trimmed.replaceAll("\\s", "");
		}
		StringBuilder base64 = new StringBuilder();
		for (String line : trimmed.split("\\R")) {
			String part = line.trim();
			if (part.isEmpty() || part.startsWith("-----")) {
				continue;
			}
			base64.append(part);
		}
		return base64.toString();
	}

}
