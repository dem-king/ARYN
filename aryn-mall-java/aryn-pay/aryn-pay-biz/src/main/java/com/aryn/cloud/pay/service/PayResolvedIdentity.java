
package com.aryn.cloud.pay.service;

import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import lombok.Builder;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 已解析的支付身份：冻结的渠道/商户/应用/环境 + 当前可用材料明文。
 *
 * <p>明文只允许在本对象生命周期内被 SDK 构造使用，禁止打印或序列化。
 */
@Getter
@Builder
public class PayResolvedIdentity {

	private final String tenantId;

	private final String configId;

	private final String revisionId;

	private final String channel;

	private final String terminalType;

	private final String appId;

	private final String mchId;

	private final String environment;

	private final String wxPublicKeyId;

	private final String buyerIdentityMode;

	private final Map<String, byte[]> materials;

	private final Map<String, String> fingerprints;

	public PayChannelEnum channelEnum() {
		return PayChannelEnum.fromCode(channel);
	}

	public PayTerminalTypeEnum terminalEnum() {
		return PayTerminalTypeEnum.fromCode(terminalType);
	}

	public boolean sameMerchantIdentity(PayResolvedIdentity other) {
		return other != null && java.util.Objects.equals(channel, other.channel)
				&& java.util.Objects.equals(terminalType, other.terminalType)
				&& java.util.Objects.equals(appId, other.appId) && java.util.Objects.equals(mchId, other.mchId)
				&& java.util.Objects.equals(environment, other.environment);
	}

	public boolean hasRequiredMaterials() {
		PayChannelEnum channelEnum = channelEnum();
		if (channelEnum == null) {
			return false;
		}
		for (PayConfigMaterialPurposeEnum purpose : PayConfigMaterialPurposeEnum.requiredFor(channelEnum)) {
			if (material(purpose) == null || material(purpose).length == 0) {
				return false;
			}
		}
		if (channelEnum == PayChannelEnum.WECHAT && (wxPublicKeyId == null || wxPublicKeyId.isBlank())) {
			return false;
		}
		return true;
	}

	public byte[] material(PayConfigMaterialPurposeEnum purpose) {
		return materials == null ? null : materials.get(purpose.getCode());
	}

	public String materialText(PayConfigMaterialPurposeEnum purpose) {
		byte[] value = material(purpose);
		return value == null ? null : new String(value, StandardCharsets.UTF_8).trim();
	}

	/**
	 * SDK 缓存键只使用身份与材料指纹，不包含任何明文。
	 */
	public String cacheKey() {
		StringBuilder builder = new StringBuilder();
		builder.append(tenantId).append('|').append(revisionId).append('|').append(channel).append('|')
			.append(terminalType).append('|');
		Map<String, String> sorted = new TreeMap<>(fingerprints == null ? Collections.emptyMap() : fingerprints);
		sorted.forEach((purpose, fingerprint) -> builder.append(purpose).append('=').append(fingerprint).append(','));
		return builder.toString();
	}

}
