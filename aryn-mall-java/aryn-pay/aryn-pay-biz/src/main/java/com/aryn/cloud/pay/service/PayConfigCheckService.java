package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigCheckResultVO;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地资料检查：只证明材料内部一致，不证明平台登记、产品签约或公网回调可达。
 */
@Service
@RequiredArgsConstructor
public class PayConfigCheckService {

	private final PayConfigMapper payConfigMapper;

	private final PayConfigRevisionMapper revisionMapper;

	private final PayConfigMaterialMapper materialMapper;

	private final PayMaterialParser materialParser;

	private final PayMaterialCipher materialCipher;

	private final PayCredentialResolver credentialResolver;

	public PayConfigCheckResultVO check(String configId, String revisionId) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (revision == null || !configId.equals(revision.getConfigId())) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayConfigCheckResultVO result = new PayConfigCheckResultVO();
		result.setConfigId(configId);
		result.setRevisionId(revisionId);
		result.setCheckedAt(LocalDateTime.now());

		if (!materialCipher.isConfigured()) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("ENCRYPTION_KEY", "主密钥可用", "支付材料主密钥未配置"));
			result.setPassed(false);
			return result;
		}
		result.getItems().add(PayConfigCheckResultVO.Item.pass("ENCRYPTION_KEY", "主密钥可用"));
		result.getItems().add(PayConfigCheckResultVO.Item.pass("IDENTITY", "商户身份字段完整"));
		if (!StringUtils.hasText(revision.getAppId()) || !StringUtils.hasText(revision.getMchId())
				|| !StringUtils.hasText(revision.getEnvironment())) {
			result.getItems().set(result.getItems().size() - 1,
					PayConfigCheckResultVO.Item.fail("IDENTITY", "商户身份字段完整", "应用ID、商户号或环境为空"));
		}
		PayChannelEnum channel = PayChannelEnum.fromCode(config.getType());
		if (channel == null || !config.getType().equals(revision.getChannel())
				|| !StringUtils.hasText(config.getTerminalType())
				|| !config.getTerminalType().equals(revision.getTerminalType())) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("ROUTE", "渠道与终端一致", "支付配置与身份版本不一致"));
		}
		else {
			result.getItems().add(PayConfigCheckResultVO.Item.pass("ROUTE", "渠道与终端一致"));
		}
		if (channel == PayChannelEnum.WECHAT && !"prod".equals(revision.getEnvironment())) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("ENVIRONMENT", "环境允许", "微信支付仅支持生产环境"));
		}
		if (channel == PayChannelEnum.WECHAT
				&& (!StringUtils.hasText(revision.getWxPublicKeyId()) || !revision.getWxPublicKeyId().startsWith("PUB_KEY_ID_"))) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("WX_PUBLIC_KEY_ID", "微信支付公钥ID",
					"必须填写 PUB_KEY_ID_ 开头的微信支付公钥ID"));
		}
		if (channel == PayChannelEnum.ALIPAY
				&& com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum.MINI_PROGRAM.getCode()
					.equals(revision.getTerminalType())) {
			if (MiniAppIdentityModeEnum.fromCode(revision.getBuyerIdentityMode()) == null) {
				result.getItems().add(PayConfigCheckResultVO.Item.fail("BUYER_IDENTITY_MODE",
						"支付宝小程序买家身份模式", "必须显式配置 user_id 或 open_id"));
			}
			else {
				result.getItems().add(PayConfigCheckResultVO.Item.pass("BUYER_IDENTITY_MODE",
						"支付宝小程序买家身份模式"));
			}
		}

		Map<String, byte[]> plaintext = new LinkedHashMap<>();
		Map<String, String> refs;
		try {
			refs = credentialResolver.parseRefs(revision.getMaterialRefs());
		}
		catch (ArynBusinessException exception) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("MATERIAL_REFS", "材料引用", exception.getMsg()));
			result.setPassed(false);
			return result;
		}
		List<PayConfigMaterialPurposeEnum> required = channel == null ? List.of()
				: PayConfigMaterialPurposeEnum.requiredFor(channel);
		for (PayConfigMaterialPurposeEnum purpose : required) {
			String materialId = refs.get(purpose.getCode());
			if (!StringUtils.hasText(materialId)) {
				result.getItems().add(PayConfigCheckResultVO.Item.fail("MATERIAL_" + purpose.getCode(),
						purpose.getCode(), "缺少必需材料"));
				continue;
			}
			PayConfigMaterial material = materialMapper.selectById(materialId);
			if (material == null || !tenantId.equals(material.getTenantId())
					|| !configId.equals(material.getConfigId()) || !purpose.getCode().equals(material.getPurpose())) {
				result.getItems().add(PayConfigCheckResultVO.Item.fail("MATERIAL_" + purpose.getCode(),
						purpose.getCode(), "材料不存在或归属不匹配"));
				continue;
			}
			try {
				byte[] content = materialCipher.decrypt(tenantId, material.getId(), material.getPurpose(),
						material.getKeyId(), material.getNonce(), material.getCiphertext());
				materialParser.parse(purpose, content);
				if (purpose == PayConfigMaterialPurposeEnum.WX_API_V3_KEY && content.length != 32) {
					throw new ArynBusinessException("微信 APIv3 密钥必须为 32 字节");
				}
				plaintext.put(purpose.getCode(), content);
				result.getItems().add(PayConfigCheckResultVO.Item.pass("MATERIAL_" + purpose.getCode(),
						purpose.getCode()));
			}
			catch (ArynBusinessException exception) {
				result.getItems().add(PayConfigCheckResultVO.Item.fail("MATERIAL_" + purpose.getCode(),
						purpose.getCode(), exception.getMsg()));
			}
		}

		checkPair(result, plaintext, PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY,
				PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT);
		checkPair(result, plaintext, PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY,
				PayConfigMaterialPurposeEnum.ALI_APP_CERT);

		result.setPassed(result.getItems().stream().allMatch(PayConfigCheckResultVO.Item::isPassed));
		return result;
	}

	private void checkPair(PayConfigCheckResultVO result, Map<String, byte[]> plaintext,
			PayConfigMaterialPurposeEnum keyPurpose, PayConfigMaterialPurposeEnum certPurpose) {
		byte[] privateKey = plaintext.get(keyPurpose.getCode());
		byte[] certificateContent = plaintext.get(certPurpose.getCode());
		if (privateKey == null || certificateContent == null) {
			return;
		}
		try {
			X509Certificate certificate = materialParser.parseCertificate(certificateContent);
			if (materialParser.keyPairMatches(privateKey, certificate)) {
				result.getItems().add(PayConfigCheckResultVO.Item.pass("PAIR_" + keyPurpose.getCode(), "私钥与证书配对"));
			}
			else {
				result.getItems().add(PayConfigCheckResultVO.Item.fail("PAIR_" + keyPurpose.getCode(), "私钥与证书配对",
						"私钥与证书不匹配"));
			}
		}
		catch (ArynBusinessException exception) {
			result.getItems().add(PayConfigCheckResultVO.Item.fail("PAIR_" + keyPurpose.getCode(), "私钥与证书配对",
					exception.getMsg()));
		}
	}

	private String requireTenant() {
		String tenantId = com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
		return tenantId;
	}

}
