package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.PayConfigMaterialRefDTO;
import com.aryn.cloud.pay.api.dto.PayConfigRevisionCreateReqDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigRevisionVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧明文支付配置迁移。
 *
 * <p>只从允许目录读取证书，材料全部加密入库；可重复执行，冲突配置阻断但不删除
 * 任何支付/退款记录。旧明文字段与文件保留，待发布流程批准后单独清理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayLegacyMigrationService {

	private final PayConfigMapper payConfigMapper;

	private final PayTradeOrderMapper payTradeOrderMapper;

	private final PayCredentialService credentialService;

	private final PayConfigRevisionService revisionService;

	@Value("${cert-dir:}")
	private String certDir;

	public MigrationReport migrate() {
		MigrationReport report = new MigrationReport();
		List<PayConfig> configs = payConfigMapper.selectList(Wrappers.<PayConfig>lambdaQuery());
		for (PayConfig config : configs) {
			try {
				ConfigResult result = migrateConfig(config);
				report.getResults().add(result);
				if ("MIGRATED".equals(result.getStatus())) {
					report.setMigrated(report.getMigrated() + 1);
				}
				else if ("SKIPPED".equals(result.getStatus())) {
					report.setSkipped(report.getSkipped() + 1);
				}
				else {
					report.setBlocked(report.getBlocked() + 1);
				}
			}
			catch (ArynBusinessException exception) {
				ConfigResult result = new ConfigResult();
				result.setConfigId(config.getId());
				result.setStatus("BLOCKED");
				result.setMessage(exception.getMsg());
				report.getResults().add(result);
				report.setBlocked(report.getBlocked() + 1);
			}
			catch (RuntimeException exception) {
				log.warn("旧支付配置迁移异常, configId: {}", config.getId());
				ConfigResult result = new ConfigResult();
				result.setConfigId(config.getId());
				result.setStatus("BLOCKED");
				result.setMessage("迁移异常，请检查材料格式后重试");
				report.getResults().add(result);
				report.setBlocked(report.getBlocked() + 1);
			}
		}
		return report;
	}

	private ConfigResult migrateConfig(PayConfig config) {
		ConfigResult result = new ConfigResult();
		result.setConfigId(config.getId());
		if (StringUtils.hasText(config.getActiveRevisionId())) {
			result.setStatus("SKIPPED");
			result.setMessage("已有生效身份版本");
			result.setRevisionId(config.getActiveRevisionId());
			return result;
		}
		PayChannelEnum channel = PayChannelEnum.fromCode(config.getType());
		if (channel == null) {
			throw new ArynBusinessException("不支持的支付渠道");
		}

		Map<PayConfigMaterialPurposeEnum, byte[]> materials = new LinkedHashMap<>();
		String wxPublicKeyId = null;
		if (channel == PayChannelEnum.WECHAT) {
			if (!StringUtils.hasText(config.getApiv3Key())) {
				throw new ArynBusinessException("旧配置缺少 APIv3 密钥，无法迁移");
			}
			materials.put(PayConfigMaterialPurposeEnum.WX_API_V3_KEY,
					config.getApiv3Key().getBytes(StandardCharsets.UTF_8));
			materials.put(PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY,
					readAllowedFile(config.getPrivateKeyPath(), "商户私钥"));
			materials.put(PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT,
					readAllowedFile(config.getPrivateCertPath(), "商户证书"));
			materials.put(PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY,
					readAllowedFile(config.getPublicKeyPath(), "微信支付公钥"));
			wxPublicKeyId = config.getPublicKeyId();
			if (!StringUtils.hasText(wxPublicKeyId)) {
				throw new ArynBusinessException("旧配置缺少微信支付公钥ID，无法迁移");
			}
		}
		else if (channel == PayChannelEnum.ALIPAY) {
			if (!StringUtils.hasText(config.getMchKey())) {
				throw new ArynBusinessException("旧配置缺少支付宝应用私钥，无法迁移");
			}
			materials.put(PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY,
					config.getMchKey().getBytes(StandardCharsets.UTF_8));
			materials.put(PayConfigMaterialPurposeEnum.ALI_APP_CERT,
					readAllowedFile(config.getPrivateCertPath(), "应用公钥证书"));
			materials.put(PayConfigMaterialPurposeEnum.ALI_PUBLIC_CERT,
					readAllowedFile(config.getPrivateKeyPath(), "支付宝公钥证书"));
			materials.put(PayConfigMaterialPurposeEnum.ALI_ROOT_CERT,
					readAllowedFile(config.getKeyPath(), "支付宝根证书"));
		}
		if (materials.isEmpty()) {
			result.setStatus("SKIPPED");
			result.setMessage("无旧版支付材料");
			return result;
		}

		List<PayConfigMaterialRefDTO> refs = new ArrayList<>();
		for (Map.Entry<PayConfigMaterialPurposeEnum, byte[]> entry : materials.entrySet()) {
			var uploaded = credentialService.storeMaterial(config.getId(), entry.getKey().getCode(),
					entry.getValue());
			PayConfigMaterialRefDTO ref = new PayConfigMaterialRefDTO();
			ref.setPurpose(entry.getKey().getCode());
			ref.setMaterialId(uploaded.getMaterialId());
			refs.add(ref);
		}
		PayConfigRevisionCreateReqDTO request = new PayConfigRevisionCreateReqDTO();
		request.setAppId(config.getAppId());
		request.setMchId(config.getMchId());
		request.setEnvironment("prod");
		request.setWxPublicKeyId(wxPublicKeyId);
		request.setMaterials(refs);
		PayConfigRevisionVO revision = revisionService.createRevision(config.getId(), request);
		revisionService.publish(config.getId(), revision.getId(), config.getConfigVersion());

		BindResult bindResult = bindHistoricalOrders(config, revision.getId());
		result.setStatus("MIGRATED");
		result.setRevisionId(revision.getId());
		result.setBoundOrders(bindResult.getBound());
		result.setUnboundOrders(bindResult.getUnbound());
		result.setMessage(bindResult.getUnbound() > 0 ? "存在无法证明旧商户身份的订单，未自动绑定" : "迁移完成");
		return result;
	}

	private BindResult bindHistoricalOrders(PayConfig config, String revisionId) {
		String prefix = PayChannelEnum.WECHAT.getCode().equals(config.getType()) ? "WX_" : "ALI_";
		List<PayTradeOrder> orders = payTradeOrderMapper.selectList(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getTerminalType, config.getTerminalType())
			.likeRight(PayTradeOrder::getTradeType, prefix)
			.and(wrapper -> wrapper.isNull(PayTradeOrder::getConfigRevisionId)
				.or()
				.eq(PayTradeOrder::getConfigRevisionId, "")));
		int bound = 0;
		for (PayTradeOrder order : orders) {
			// 只有渠道商户号与当前配置一致时，才有证据把旧订单绑定到该身份
			if (StringUtils.hasText(order.getChannelMchId()) && order.getChannelMchId().equals(config.getMchId())) {
				bound += payTradeOrderMapper.bindRevisionIfAbsent(order.getTenantId(), order.getId(), revisionId);
			}
		}
		return new BindResult(bound, orders.size() - bound);
	}

	private byte[] readAllowedFile(String path, String purposeName) {
		if (!StringUtils.hasText(path)) {
			throw new ArynBusinessException("旧配置缺少" + purposeName);
		}
		if (path.startsWith("classpath:") || path.contains("://")) {
			throw new ArynBusinessException(purposeName + "不在允许的证书目录内，请重新上传");
		}
		if (!StringUtils.hasText(certDir)) {
			throw new ArynBusinessException("未配置证书目录，禁止自动迁移");
		}
		try {
			Path root = Paths.get(certDir).toAbsolutePath().normalize();
			Path target = Paths.get(path).toAbsolutePath().normalize();
			if (!target.startsWith(root) || !Files.isRegularFile(target)) {
				throw new ArynBusinessException(purposeName + "不在允许的证书目录内，请重新上传");
			}
			return Files.readAllBytes(target);
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ArynBusinessException(purposeName + "读取失败，请重新上传");
		}
	}

	@Data
	public static class MigrationReport {

		private int migrated;

		private int skipped;

		private int blocked;

		private List<ConfigResult> results = new ArrayList<>();

	}

	@Data
	public static class ConfigResult {

		private String configId;

		private String status;

		private String message;

		private String revisionId;

		private int boundOrders;

		private int unboundOrders;

	}

	@Data
	@RequiredArgsConstructor
	private static class BindResult {

		private final int bound;

		private final int unbound;

	}

}
