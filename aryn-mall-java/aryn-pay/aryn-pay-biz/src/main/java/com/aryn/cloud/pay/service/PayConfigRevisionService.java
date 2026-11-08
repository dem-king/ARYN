package com.aryn.cloud.pay.service;

import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.PayConfigMaterialRefDTO;
import com.aryn.cloud.pay.api.dto.PayConfigRevisionCreateReqDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigCheckResultVO;
import com.aryn.cloud.pay.api.vo.PayConfigMaterialVO;
import com.aryn.cloud.pay.api.vo.PayConfigRevisionVO;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付身份版本：草稿可缺材料，启用必须通过本地检查；版本一经创建不可修改。
 */
@Service
@RequiredArgsConstructor
public class PayConfigRevisionService {

	private final PayConfigMapper payConfigMapper;

	private final PayConfigRevisionMapper revisionMapper;

	private final PayConfigMaterialMapper materialMapper;

	private final PayMaterialCipher materialCipher;

	private final PayConfigCheckService checkService;

	@Transactional(rollbackFor = Exception.class)
	public PayConfigRevisionVO createRevision(String configId, PayConfigRevisionCreateReqDTO request) {
		if (request == null) {
			throw new ArynBusinessException("支付身份版本请求不能为空");
		}
		if (request.getMaterials() == null) {
			request.setMaterials(new ArrayList<>());
		}
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		PayChannelEnum channel = PayChannelEnum.fromCode(config.getType());
		if (channel == null) {
			throw new ArynBusinessException("支付配置渠道无效");
		}
		String environment = StringUtils.hasText(request.getEnvironment()) ? request.getEnvironment().trim() : "prod";
		if (!"prod".equals(environment) && !"sandbox".equals(environment)) {
			throw new ArynBusinessException("支付环境仅支持 prod 或 sandbox");
		}
		if (channel == PayChannelEnum.WECHAT && !"prod".equals(environment)) {
			throw new ArynBusinessException("微信支付不支持沙箱环境");
		}
		String buyerIdentityMode = normalizeBuyerIdentityMode(channel, config.getTerminalType(),
				request.getBuyerIdentityMode());

		Map<String, PayConfigMaterial> materials = new LinkedHashMap<>();
		for (PayConfigMaterialRefDTO ref : request.getMaterials()) {
			if (ref == null) {
				throw new ArynBusinessException("支付材料引用不能为空");
			}
			PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(ref.getPurpose());
			if (purpose == null) {
				throw new ArynBusinessException("不支持的支付材料用途");
			}
			if (materials.containsKey(purpose.getCode())) {
				throw new ArynBusinessException("支付材料用途重复");
			}
			if (purpose.getChannel() != channel) {
				throw new ArynBusinessException("支付材料用途与支付渠道不匹配");
			}
			PayConfigMaterial material = materialMapper.selectById(ref.getMaterialId());
			if (material == null || !tenantId.equals(material.getTenantId())
					|| !configId.equals(material.getConfigId()) || !purpose.getCode().equals(material.getPurpose())) {
				throw new ArynBusinessException("支付材料不存在或归属不匹配");
			}
			materials.put(purpose.getCode(), material);
		}

		String identityHash = identityHash(config, request, environment, buyerIdentityMode, materials);
		PayConfigRevision existing = revisionMapper.selectByIdentityHash(tenantId, configId, identityHash);
		if (existing != null) {
			return toVO(existing);
		}

		JSONObject refs = new JSONObject();
		materials.forEach((purpose, material) -> refs.put(purpose, material.getId()));
		List<String> missing = missingPurposes(channel, materials);
		boolean ready = missing.isEmpty() && materialCipher.isConfigured();

		PayConfigRevision revision = new PayConfigRevision();
		revision.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
		revision.setTenantId(tenantId);
		revision.setConfigId(configId);
		revision.setRevisionNo(revisionMapper.selectMaxRevisionNo(tenantId, configId) + 1);
		revision.setChannel(config.getType());
		revision.setTerminalType(config.getTerminalType());
		revision.setAppId(request.getAppId().trim());
		revision.setMchId(request.getMchId().trim());
		revision.setEnvironment(environment);
		revision.setWxPublicKeyId(request.getWxPublicKeyId());
		revision.setBuyerIdentityMode(buyerIdentityMode);
		revision.setMaterialRefs(refs.toJSONString());
		revision.setIdentityHash(identityHash);
		revision.setCheckStatus(ready ? "1" : "0");
		revision.setCheckSummary(ready ? "本地材料齐备，未做实付验证"
				: "材料未齐备：" + String.join(",", missing));
		revisionMapper.insert(revision);
		return toVO(revision);
	}

	@Transactional(rollbackFor = Exception.class)
	public PayConfigRevisionVO publish(String configId, String revisionId, Integer expectedVersion) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (revision == null || !configId.equals(revision.getConfigId())) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		PayConfigCheckResultVO check = checkService.check(configId, revisionId);
		if (!check.isPassed()) {
			String reason = check.getItems()
				.stream()
				.filter(item -> !item.isPassed())
				.map(item -> item.getName() + "：" + (item.getMessage() == null ? "未通过" : item.getMessage()))
				.findFirst()
				.orElse("本地检查未通过");
			throw new ArynBusinessException("启用失败：" + reason);
		}
		int version = expectedVersion != null ? expectedVersion
				: (config.getConfigVersion() == null ? 0 : config.getConfigVersion());
		int updated;
		try {
			updated = payConfigMapper.activateRevision(tenantId, configId, revisionId, revision.getAppId(),
					revision.getMchId(), version);
		}
		catch (org.springframework.dao.DuplicateKeyException exception) {
			throw new ArynBusinessException("该应用ID已被其他支付配置使用，不能启用");
		}
		if (updated == 0) {
			throw new ArynBusinessException("支付配置已被其他操作修改，请刷新后重试");
		}
		return toVO(revision);
	}

	@Transactional(rollbackFor = Exception.class)
	public void disable(String configId, Integer expectedVersion) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		int version = expectedVersion != null ? expectedVersion
				: (config.getConfigVersion() == null ? 0 : config.getConfigVersion());
		if (payConfigMapper.disable(tenantId, configId, version) == 0) {
			throw new ArynBusinessException("支付配置已被其他操作修改，请刷新后重试");
		}
	}

	public List<PayConfigRevisionVO> listRevisions(String configId) {
		String tenantId = requireTenant();
		PayConfig config = payConfigMapper.selectByTenantAndId(tenantId, configId);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		return revisionMapper
			.selectList(Wrappers.<PayConfigRevision>lambdaQuery()
				.eq(PayConfigRevision::getConfigId, configId)
				.orderByDesc(PayConfigRevision::getRevisionNo))
			.stream()
			.map(this::toVO)
			.toList();
	}

	public PayConfigRevisionVO getRevision(String configId, String revisionId) {
		String tenantId = requireTenant();
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, revisionId);
		if (revision == null || !configId.equals(revision.getConfigId())) {
			throw new ArynBusinessException("支付身份版本不存在");
		}
		return toVO(revision);
	}

	public PayConfigRevisionVO toVO(PayConfigRevision revision) {
		PayConfigRevisionVO vo = new PayConfigRevisionVO();
		vo.setId(revision.getId());
		vo.setConfigId(revision.getConfigId());
		vo.setRevisionNo(revision.getRevisionNo());
		vo.setChannel(revision.getChannel());
		vo.setTerminalType(revision.getTerminalType());
		vo.setAppId(revision.getAppId());
		vo.setMchId(revision.getMchId());
		vo.setEnvironment(revision.getEnvironment());
		vo.setWxPublicKeyId(revision.getWxPublicKeyId());
		vo.setBuyerIdentityMode(revision.getBuyerIdentityMode());
		vo.setCheckStatus(revision.getCheckStatus());
		vo.setCheckSummary(revision.getCheckSummary());
		vo.setCreateTime(revision.getCreateTime());
		Map<String, String> refs;
		try {
			refs = com.alibaba.fastjson2.JSON.parseObject(revision.getMaterialRefs())
				.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue()),
						(left, right) -> left, LinkedHashMap::new));
		}
		catch (RuntimeException exception) {
			refs = Map.of();
		}
		if (!refs.isEmpty()) {
			Map<String, PayConfigMaterial> materials = materialMapper
				.selectByTenantAndIds(revision.getTenantId(), refs.values())
				.stream()
				.collect(Collectors.toMap(PayConfigMaterial::getId, material -> material, (left, right) -> left));
			List<PayConfigMaterialVO> materialVOs = new ArrayList<>();
			for (Map.Entry<String, String> entry : refs.entrySet()) {
				PayConfigMaterial material = materials.get(entry.getValue());
				if (material == null) {
					continue;
				}
				PayConfigMaterialPurposeEnum purpose = PayConfigMaterialPurposeEnum.fromCode(entry.getKey());
				PayConfigMaterialVO materialVO = new PayConfigMaterialVO();
				materialVO.setMaterialId(material.getId());
				materialVO.setPurpose(material.getPurpose());
				materialVO.setFingerprint(material.getFingerprint());
				materialVO.setContentType(material.getContentType());
				materialVO.setByteSize(material.getByteSize());
				materialVO.setCertSerialNo(material.getCertSerialNo());
				materialVO.setCertNotBefore(material.getCertNotBefore());
				materialVO.setCertNotAfter(material.getCertNotAfter());
				materialVO.setCreateTime(material.getCreateTime());
				materialVO.setSensitive(purpose != null && purpose.isSensitive());
				materialVOs.add(materialVO);
			}
			materialVOs.sort(Comparator.comparing(PayConfigMaterialVO::getPurpose));
			vo.setMaterials(materialVOs);
		}
		return vo;
	}

	private List<String> missingPurposes(PayChannelEnum channel, Map<String, PayConfigMaterial> materials) {
		List<String> missing = new ArrayList<>();
		for (PayConfigMaterialPurposeEnum purpose : PayConfigMaterialPurposeEnum.requiredFor(channel)) {
			if (!materials.containsKey(purpose.getCode())) {
				missing.add(purpose.getCode());
			}
		}
		return missing;
	}

	/**
	 * 买家身份模式是支付宝小程序的支付身份组成部分，必须显式配置；
	 * 其它渠道不允许携带该字段，避免静默忽略造成身份歧义。
	 */
	private String normalizeBuyerIdentityMode(PayChannelEnum channel, String terminalType, String requested) {
		boolean alipayMini = channel == PayChannelEnum.ALIPAY
				&& com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum.MINI_PROGRAM.getCode().equals(terminalType);
		if (!alipayMini) {
			if (StringUtils.hasText(requested)) {
				throw new ArynBusinessException("仅支付宝小程序可配置买家身份模式");
			}
			return null;
		}
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(requested);
		if (mode == null) {
			throw new ArynBusinessException("支付宝小程序必须配置买家身份模式 user_id 或 open_id");
		}
		return mode.getCode();
	}

	private String identityHash(PayConfig config, PayConfigRevisionCreateReqDTO request, String environment,
			String buyerIdentityMode, Map<String, PayConfigMaterial> materials) {
		StringBuilder builder = new StringBuilder();
		builder.append(config.getType()).append('|').append(config.getTerminalType()).append('|')
			.append(request.getAppId().trim()).append('|').append(request.getMchId().trim()).append('|')
			.append(environment).append('|').append(request.getWxPublicKeyId() == null ? "" : request.getWxPublicKeyId())
			.append('|');
		// 仅支付宝小程序追加身份模式，保持其它渠道历史身份指纹不变
		if (buyerIdentityMode != null) {
			builder.append(buyerIdentityMode).append('|');
		}
		new java.util.TreeMap<>(materials).forEach((purpose, material) -> builder.append(purpose).append('=')
			.append(material.getFingerprint())
			.append(','));
		return sha256Hex(builder.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String sha256Hex(byte[] content) {
		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (Exception exception) {
			throw new ArynBusinessException("支付身份指纹计算失败");
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
