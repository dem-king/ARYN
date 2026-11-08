package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigMaterialUploadVO;
import com.aryn.cloud.pay.config.PayCredentialProperties;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * 支付材料上传与校验。材料加密入库，私钥/APIv3 密钥不回显、不落服务器路径。
 */
@Service
@RequiredArgsConstructor
public class PayCredentialService {

	private final PayConfigMapper payConfigMapper;

	private final PayConfigMaterialMapper materialMapper;

	private final PayMaterialParser materialParser;

	private final PayMaterialCipher materialCipher;

	private final PayCredentialProperties properties;

	public PayConfigMaterialUploadVO uploadMaterial(String configId, String purposeCode, MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ArynBusinessException("支付材料文件不能为空");
		}
		byte[] content;
		try {
			content = file.getBytes();
		}
		catch (IOException exception) {
			throw new ArynBusinessException("支付材料读取失败");
		}
		return storeMaterial(configId, purposeCode, content);
	}

	public PayConfigMaterialUploadVO storeMaterial(String configId, String purposeCode, byte[] content) {		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(purposeCode);
		if (purpose == null) {
			throw new ArynBusinessException("不支持的支付材料用途");
		}
		PayChannelEnum channel = PayChannelEnum.fromCode(config.getType());
		if (channel == null) {
			throw new ArynBusinessException("支付配置渠道无效");
		}
		materialParser.assertChannelMatches(purpose, channel);
		if (content == null || content.length == 0) {
			throw new ArynBusinessException("支付材料内容为空");
		}
		if (content.length > properties.getMaxMaterialBytes()) {
			throw new ArynBusinessException("支付材料不能超过 " + (properties.getMaxMaterialBytes() / 1024) + "KB");
		}
		if (!materialCipher.isConfigured()) {
			throw new ArynBusinessException("支付材料主密钥未配置，禁止保存材料");
		}
		if (purpose == PayConfigMaterialPurposeEnum.WX_API_V3_KEY) {
			content = normalizeApiV3Key(content);
		}
		PayMaterialParser.ParsedMaterial parsed = materialParser.parse(purpose, content);
		Boolean keyPairMatched = checkPairing(config, purpose, content);
		String fingerprint = materialCipher.fingerprint(content);
		PayConfigMaterial existing = materialMapper.selectOne(
				com.baomidou.mybatisplus.core.toolkit.Wrappers.<PayConfigMaterial>lambdaQuery()
					.eq(PayConfigMaterial::getConfigId, configId)
					.eq(PayConfigMaterial::getPurpose, purpose.getCode())
					.eq(PayConfigMaterial::getFingerprint, fingerprint)
					.orderByDesc(PayConfigMaterial::getCreateTime)
					.last("LIMIT 1"));
		if (existing != null) {
			return toUploadVO(existing, keyPairMatched);
		}

		String materialId = IdWorker.getIdStr();
		PayMaterialCipher.EncryptedMaterial encrypted = materialCipher.encrypt(tenantId, materialId, purpose.getCode(),
				content);
		PayConfigMaterial material = new PayConfigMaterial().setId(materialId)
			.setTenantId(tenantId)
			.setConfigId(configId)
			.setPurpose(purpose.getCode())
			.setCiphertext(encrypted.getCiphertext())
			.setNonce(encrypted.getNonce())
			.setKeyId(encrypted.getKeyId())
			.setAlgorithm(encrypted.getAlgorithm())
			.setFingerprint(fingerprint)
			.setContentType(parsed.getContentType())
			.setCertSerialNo(parsed.getCertSerialNo())
			.setCertNotBefore(parsed.getCertNotBefore())
			.setCertNotAfter(parsed.getCertNotAfter())
			.setByteSize(content.length);
		materialMapper.insert(material);
		return toUploadVO(material, keyPairMatched);
	}

	private PayConfigMaterialUploadVO toUploadVO(PayConfigMaterial material, Boolean keyPairMatched) {
		PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(material.getPurpose());
		PayConfigMaterialUploadVO vo = new PayConfigMaterialUploadVO();
		vo.setMaterialId(material.getId());
		vo.setPurpose(material.getPurpose());
		vo.setFingerprint(material.getFingerprint());
		vo.setContentType(material.getContentType());
		vo.setByteSize(material.getByteSize());
		vo.setCertSerialNo(material.getCertSerialNo());
		vo.setCertNotBefore(material.getCertNotBefore());
		vo.setCertNotAfter(material.getCertNotAfter());
		vo.setSensitive(purpose != null && purpose.isSensitive());
		vo.setKeyPairMatched(keyPairMatched);
		return vo;
	}

	public java.util.List<com.aryn.cloud.pay.api.vo.PayConfigMaterialVO> listMaterials(String configId) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		return materialMapper
			.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<PayConfigMaterial>lambdaQuery()
				.eq(PayConfigMaterial::getConfigId, configId)
				.orderByDesc(PayConfigMaterial::getCreateTime))
			.stream()
			.map(material -> {
				PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(material.getPurpose());
				com.aryn.cloud.pay.api.vo.PayConfigMaterialVO vo = new com.aryn.cloud.pay.api.vo.PayConfigMaterialVO();
				vo.setMaterialId(material.getId());
				vo.setPurpose(material.getPurpose());
				vo.setFingerprint(material.getFingerprint());
				vo.setContentType(material.getContentType());
				vo.setByteSize(material.getByteSize());
				vo.setCertSerialNo(material.getCertSerialNo());
				vo.setCertNotBefore(material.getCertNotBefore());
				vo.setCertNotAfter(material.getCertNotAfter());
				vo.setCreateTime(material.getCreateTime());
				vo.setSensitive(purpose != null && purpose.isSensitive());
				return vo;
			})
			.toList();
	}

	public PayConfigMaterial requireMaterial(String tenantId, String configId, String materialId,
			PayConfigMaterialPurposeEnum purpose) {
		PayConfigMaterial material = materialMapper.selectById(materialId);
		if (material == null || !tenantId.equals(material.getTenantId()) || !configId.equals(material.getConfigId())
				|| !purpose.getCode().equals(material.getPurpose())) {
			throw new ArynBusinessException("支付材料不存在或归属不匹配");
		}
		return material;
	}

	public byte[] decrypt(PayConfigMaterial material) {
		return materialCipher.decrypt(material.getTenantId(), material.getId(), material.getPurpose(),
				material.getKeyId(), material.getNonce(), material.getCiphertext());
	}

	private byte[] normalizeApiV3Key(byte[] content) {
		String text = new String(content, StandardCharsets.UTF_8).trim();
		byte[] raw = text.getBytes(StandardCharsets.UTF_8);
		if (raw.length == 32) {
			return raw;
		}
		try {
			byte[] decoded = Base64.getDecoder().decode(text);
			if (decoded.length == 32) {
				return decoded;
			}
		}
		catch (IllegalArgumentException ignored) {
			// 继续走统一失败提示，不回显内容
		}
		throw new ArynBusinessException("微信 APIv3 密钥必须为 32 字节");
	}

	/**
	 * 上传私钥或应用证书时，若同配置已有对应材料则立即检查配对；不匹配直接拒绝。
	 */
	private Boolean checkPairing(PayConfig config, PayConfigMaterialPurposeEnum purpose, byte[] content) {
		PayConfigMaterialPurposeEnum counterpartPurpose = switch (purpose) {
			case WX_MERCHANT_PRIVATE_KEY -> PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT;
			case WX_MERCHANT_CERT -> PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY;
			case ALI_APP_PRIVATE_KEY -> PayConfigMaterialPurposeEnum.ALI_APP_CERT;
			case ALI_APP_CERT -> PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY;
			default -> null;
		};
		if (counterpartPurpose == null) {
			return null;
		}
		PayConfigMaterial counterpart = materialMapper.selectOne(
				com.baomidou.mybatisplus.core.toolkit.Wrappers.<PayConfigMaterial>lambdaQuery()
					.eq(PayConfigMaterial::getConfigId, config.getId())
					.eq(PayConfigMaterial::getPurpose, counterpartPurpose.getCode())
					.orderByDesc(PayConfigMaterial::getCreateTime)
					.last("LIMIT 1"));
		if (counterpart == null) {
			return null;
		}
		byte[] counterpartContent = decrypt(counterpart);
		boolean matched;
		if (purpose == PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY
				|| purpose == PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY) {
			X509Certificate certificate = materialParser.parseCertificate(counterpartContent);
			matched = materialParser.keyPairMatches(content, certificate);
		}
		else if (purpose == PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT
				|| purpose == PayConfigMaterialPurposeEnum.ALI_APP_CERT) {
			X509Certificate certificate = materialParser.parseCertificate(content);
			matched = materialParser.keyPairMatches(counterpartContent, certificate);
		}
		else {
			return null;
		}
		if (!matched) {
			throw new ArynBusinessException("证书与私钥不匹配");
		}
		return true;
	}

	private String requireTenant() {
		String tenantId = com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
		return tenantId;
	}

}
