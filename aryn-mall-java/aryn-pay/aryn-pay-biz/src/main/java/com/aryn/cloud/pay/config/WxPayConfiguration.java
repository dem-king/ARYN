package com.aryn.cloud.pay.config;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

/**
 * 微信支付 APIv3 公钥模式。SDK 只消费内存中的私钥/证书内容，不读取服务器路径。
 */
@Component
@RequiredArgsConstructor
public class WxPayConfiguration {

	private final PayMaterialParser materialParser;

	public WxPayService create(PayResolvedIdentity identity) {
		byte[] privateKey = identity.material(PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY);
		byte[] privateCert = identity.material(PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT);
		byte[] publicKey = identity.material(PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY);
		String apiV3Key = identity.materialText(PayConfigMaterialPurposeEnum.WX_API_V3_KEY);
		if (privateKey == null || privateCert == null || publicKey == null || apiV3Key == null) {
			throw new ArynBusinessException("微信支付材料不完整");
		}
		X509Certificate certificate = materialParser.parseCertificate(privateCert);
		WxPayConfig wxPayConfig = new WxPayConfig();
		wxPayConfig.setAppId(identity.getAppId());
		wxPayConfig.setMchId(identity.getMchId());
		wxPayConfig.setApiV3Key(apiV3Key);
		wxPayConfig.setCertSerialNo(certificate.getSerialNumber().toString(16).toUpperCase());
		wxPayConfig.setPrivateKeyContent(privateKey);
		wxPayConfig.setPrivateCertContent(privateCert);
		wxPayConfig.setPublicKeyId(identity.getWxPublicKeyId());
		wxPayConfig.setPublicKeyContent(publicKey);
		wxPayConfig.setFullPublicKeyModel(true);
		WxPayServiceImpl wxPayService = new WxPayServiceImpl();
		wxPayService.setConfig(wxPayConfig);
		return wxPayService;
	}

	/**
	 * 供检查/测试读取构造出的 SDK 配置，不暴露到接口层。
	 */
	public WxPayConfig buildConfig(PayResolvedIdentity identity) {
		return (WxPayConfig) create(identity).getConfig();
	}

}
