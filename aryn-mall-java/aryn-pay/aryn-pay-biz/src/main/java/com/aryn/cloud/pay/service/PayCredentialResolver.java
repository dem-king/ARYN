
package com.aryn.cloud.pay.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按原交易身份解析支付材料。
 *
 * <p>换商户不会污染历史订单：订单冻结的身份版本决定商户/AppID/环境；
 * 同商户换证时优先使用该身份当前生效版本的有效签名凭据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCredentialResolver {

	private final PayConfigMapper payConfigMapper;

	private final PayConfigRevisionMapper revisionMapper;

	private final PayConfigMaterialMapper materialMapper;

	private final PayMaterialCipher materialCipher;

	public PayResolvedIdentity resolveNewPayment(String configId) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		if (!StringUtils.hasText(config.getActiveRevisionId()) || !"1".equals(config.getEnabled())) {
			throw new ArynBusinessException("支付渠道未启用");
		}
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, config.getActiveRevisionId());
		if (revision == null) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayResolvedIdentity identity = buildIdentity(tenantId, config, revision);
		if (!identity.hasRequiredMaterials()) {
			throw new ArynBusinessException("支付渠道材料不完整，无法发起支付");
		}
		return identity;
	}

	public PayResolvedIdentity resolveNewPayment(String channel, String terminalType) {
		String tenantId = requireTenant();
		com.aryn.cloud.pay.api.vo.PayConfigVO config = payConfigMapper.selectConfig(channel, terminalType);
		if (config == null) {
			throw new ArynBusinessException("未配置该支付方式");
		}
		if (!"1".equals(config.getEnabled()) || !StringUtils.hasText(config.getActiveRevisionId())) {
			throw new ArynBusinessException("支付渠道未启用");
		}
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, config.getActiveRevisionId());
		if (revision == null) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayConfig stored = payConfigMapper.selectByTenantAndId(tenantId, config.getId());
		PayResolvedIdentity identity = buildIdentity(tenantId, stored, revision);
		if (!identity.hasRequiredMaterials()) {
			throw new ArynBusinessException("支付渠道材料不完整，无法发起支付");
		}
		return identity;
	}

	public PayResolvedIdentity resolveByRevisionExact(String tenantId, String revisionId) {
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (revision == null) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, revision.getConfigId());
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		return buildIdentity(tenantId, config, revision);
	}

	public PayConfigRevision revision(String tenantId, String revisionId) {
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (revision == null) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		return revision;
	}

	public boolean sameIdentity(PayConfigRevision left, PayResolvedIdentity right) {
		return right != null && java.util.Objects.equals(left.getChannel(), right.getChannel())
				&& java.util.Objects.equals(left.getTerminalType(), right.getTerminalType())
				&& java.util.Objects.equals(left.getAppId(), right.getAppId())
				&& java.util.Objects.equals(left.getMchId(), right.getMchId())
				&& java.util.Objects.equals(left.getEnvironment(), right.getEnvironment())
				&& java.util.Objects.equals(left.getBuyerIdentityMode(), right.getBuyerIdentityMode());
	}

	/**
	 * 回调/退款验签候选：同身份当前生效版本优先，原交易版本作为轮换过渡回退。
	 * 任何候选都只用于验签，最终仍以订单冻结身份校验商户、金额与订单。
	 */
	public List<PayResolvedIdentity> resolveForSignature(String tenantId, String revisionId) {
		PayConfigRevision original = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (original == null) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, original.getConfigId());
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		List<PayResolvedIdentity> candidates = new ArrayList<>();
		if (StringUtils.hasText(config.getActiveRevisionId())
				&& !config.getActiveRevisionId().equals(original.getId())) {
			PayConfigRevision active = revisionMapper.selectByTenantAndId(tenantId, config.getActiveRevisionId());
			if (active != null && sameIdentity(original, active)) {
				try {
					candidates.add(buildIdentity(tenantId, config, active));
				}
				catch (ArynBusinessException exception) {
					log.warn("当前支付身份材料不可用，回退原版本验签, tenantId: {}, revisionId: {}", tenantId,
							active.getId());
				}
			}
		}
		try {
			candidates.add(buildIdentity(tenantId, config, original));
		}
		catch (ArynBusinessException exception) {
			if (candidates.isEmpty()) {
				throw exception;
			}
			log.warn("原支付身份材料不可用，使用当前同身份材料验签, tenantId: {}, revisionId: {}", tenantId,
					original.getId());
		}
		return candidates;
	}

	public PayResolvedIdentity resolveForOrder(String tenantId, PayTradeOrder order) {
		if (order == null || !StringUtils.hasText(order.getConfigRevisionId())) {
			throw new ArynBusinessException("历史支付单缺少支付身份版本，请先完成支付配置迁移");
		}
		List<PayResolvedIdentity> candidates = resolveForSignature(tenantId, order.getConfigRevisionId());
		return candidates.stream()
			.filter(PayResolvedIdentity::hasRequiredMaterials)
			.findFirst()
			.orElseThrow(() -> new ArynBusinessException("原交易支付身份材料不可用"));
	}

	public PayResolvedIdentity buildIdentity(String tenantId, PayConfig config, PayConfigRevision revision) {
		Map<String, String> refs = parseRefs(revision.getMaterialRefs());
		if (refs.isEmpty()) {
			throw new ArynBusinessException("支付身份版本缺少材料");
		}
		List<PayConfigMaterial> materials = materialMapper.selectByTenantAndIds(tenantId, refs.values());
		Map<String, PayConfigMaterial> byId = new LinkedHashMap<>();
		for (PayConfigMaterial material : materials) {
			byId.put(material.getId(), material);
		}
		Map<String, byte[]> plaintext = new LinkedHashMap<>();
		Map<String, String> fingerprints = new LinkedHashMap<>();
		refs.forEach((purpose, materialId) -> {
			PayConfigMaterial material = byId.get(materialId);
			if (material == null || !tenantId.equals(material.getTenantId())
					|| !config.getId().equals(material.getConfigId()) || !purpose.equals(material.getPurpose())) {
				throw new ArynBusinessException("支付材料归属或用途不匹配");
			}
			byte[] content = materialCipher.decrypt(tenantId, material.getId(), purpose, material.getKeyId(),
					material.getNonce(), material.getCiphertext());
			plaintext.put(purpose, content);
			fingerprints.put(purpose, material.getFingerprint());
		});
		return PayResolvedIdentity.builder()
			.tenantId(tenantId)
			.configId(config.getId())
			.revisionId(revision.getId())
			.channel(revision.getChannel())
			.terminalType(revision.getTerminalType())
			.appId(revision.getAppId())
			.mchId(revision.getMchId())
			.environment(revision.getEnvironment())
			.wxPublicKeyId(revision.getWxPublicKeyId())
			.buyerIdentityMode(revision.getBuyerIdentityMode())
			.materials(Map.copyOf(plaintext))
			.fingerprints(Map.copyOf(fingerprints))
			.build();
	}

	/**
	 * 材料引用只接受用途枚举，拒绝未知键，避免未来新增字段被静默忽略。
	 */
	public Map<String, String> parseRefs(String refs) {
		Map<String, String> result = new LinkedHashMap<>();
		if (!StringUtils.hasText(refs)) {
			return result;
		}
		JSONObject json;
		try {
			json = JSON.parseObject(refs);
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("支付材料引用格式错误");
		}
		for (String key : json.keySet()) {
			PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(key);
			String value = json.getString(key);
			if (purpose == null || !StringUtils.hasText(value)) {
				throw new ArynBusinessException("支付材料引用包含未知用途");
			}
			result.put(key, value);
		}
		return result;
	}

	private String requireTenant() {
		String tenantId = com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException("缺少租户上下文");
		}
		return tenantId;
	}

	private boolean sameIdentity(PayConfigRevision left, PayConfigRevision right) {
		return java.util.Objects.equals(left.getChannel(), right.getChannel())
				&& java.util.Objects.equals(left.getTerminalType(), right.getTerminalType())
				&& java.util.Objects.equals(left.getAppId(), right.getAppId())
				&& java.util.Objects.equals(left.getMchId(), right.getMchId())
				&& java.util.Objects.equals(left.getEnvironment(), right.getEnvironment())
				&& java.util.Objects.equals(left.getBuyerIdentityMode(), right.getBuyerIdentityMode());
	}

}
