
package com.aryn.cloud.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 支付配置路由。密钥与证书不在此服务保存，统一走 {@link com.aryn.cloud.pay.service.PayCredentialService}。
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Service
@RequiredArgsConstructor
public class PayConfigServiceImpl extends ServiceImpl<PayConfigMapper, PayConfig> implements IPayConfigService {

	private final PayConfigRevisionMapper revisionMapper;

	private final PayTradeOrderMapper payTradeOrderMapper;

	@Override
	public PayConfig getByAppId(String appId) {
		return baseMapper.selectByAppId(appId);
	}

	@Override
	public PayConfigVO getConfig(String payType, String terminalType) {
		return baseMapper.selectConfig(payType, terminalType);
	}

	@Override
	public boolean saveConfig(PayConfig payConfig) {
		validateConfig(payConfig);
		rejectLegacySecrets(payConfig);
		assertUnique(payConfig.getType(), payConfig.getTerminalType(), null);
		payConfig.setId(null);
		payConfig.setEnabled("0");
		payConfig.setActiveRevisionId(null);
		payConfig.setConfigVersion(0);
		payConfig.setTenantId(null);
		payConfig.setDelFlag(null);
		return this.save(payConfig);
	}

	@Override
	public boolean updateConfig(PayConfig payConfig) {
		if (!StringUtils.hasText(payConfig.getId())) {
			throw new ArynBusinessException("支付配置ID为空");
		}
		PayConfig target = this.getById(payConfig.getId());
		if (target == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		validateConfig(payConfig);
		rejectLegacySecrets(payConfig);
		assertUnique(payConfig.getType(), payConfig.getTerminalType(), payConfig.getId());
		if (StringUtils.hasText(target.getActiveRevisionId()) && identityChanged(target, payConfig)) {
			throw new ArynBusinessException("已启用的支付配置不能直接修改渠道/应用/商户，请新建身份版本后启用");
		}
		PayConfig update = new PayConfig();
		update.setId(payConfig.getId());
		update.setType(payConfig.getType());
		update.setTerminalType(payConfig.getTerminalType());
		update.setAppId(payConfig.getAppId());
		update.setMchId(payConfig.getMchId());
		return this.updateById(update);
	}

	@Override
	public Page<PayConfigVO> pageSafe(Page<PayConfig> page, PayConfig query) {
		Page<PayConfig> entityPage = this.page(page, Wrappers.lambdaQuery(query));
		Page<PayConfigVO> result = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
		result.setRecords(entityPage.getRecords().stream().map(this::toVO).toList());
		return result;
	}

	@Override
	public PayConfigVO getDetail(String id) {
		PayConfig config = this.getById(id);
		if (config == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		return toVO(config);
	}

	@Override
	public boolean deleteConfig(String id) {
		PayConfig target = this.getById(id);
		if (target == null) {
			throw new ArynBusinessException("支付配置不存在");
		}
		PayChannelEnum channel = PayChannelEnum.fromCode(target.getType());
		String prefix = channel == PayChannelEnum.WECHAT ? "WX_" : "ALI_";
		// 不只看已绑定身份版本的订单：未绑定的存量单同样是资金记录，不能让配置先消失。
		if (channel == null || payTradeOrderMapper.countByChannelTerminal(target.getTenantId(),
				target.getTerminalType(), prefix) > 0) {
			throw new ArynBusinessException("该支付配置已关联历史交易，不能删除；可停用并保留历史查询与退款");
		}
		return this.removeById(id);
	}

	private PayConfigVO toVO(PayConfig config) {
		PayConfigVO vo = new PayConfigVO();
		vo.setId(config.getId());
		vo.setType(config.getType());
		vo.setAppId(config.getAppId());
		vo.setMchId(config.getMchId());
		vo.setTerminalType(config.getTerminalType());
		vo.setTenantId(config.getTenantId());
		vo.setEnabled(config.getEnabled());
		vo.setActiveRevisionId(config.getActiveRevisionId());
		vo.setConfigVersion(config.getConfigVersion());
		vo.setCreateBy(config.getCreateBy());
		vo.setUpdateBy(config.getUpdateBy());
		vo.setCreateTime(config.getCreateTime());
		vo.setUpdateTime(config.getUpdateTime());
		vo.setMaterialReady(false);
		if (StringUtils.hasText(config.getActiveRevisionId())) {
			PayConfigRevision active = revisionMapper.selectById(config.getActiveRevisionId());
			if (active != null) {
				vo.setCheckStatus(active.getCheckStatus());
				vo.setMaterialReady(hasAllRequiredMaterials(config, active));
			}
		}
		return vo;
	}

	private boolean hasAllRequiredMaterials(PayConfig config, PayConfigRevision revision) {
		PayChannelEnum channel = PayChannelEnum.fromCode(config.getType());
		if (channel == null) {
			return false;
		}
		Map<String, String> refs;
		try {
			refs = com.alibaba.fastjson2.JSON.parseObject(revision.getMaterialRefs())
				.entrySet()
				.stream()
				.collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
						entry -> String.valueOf(entry.getValue())));
		}
		catch (RuntimeException exception) {
			return false;
		}
		for (PayConfigMaterialPurposeEnum purpose : PayConfigMaterialPurposeEnum.requiredFor(channel)) {
			if (!refs.containsKey(purpose.getCode())) {
				return false;
			}
		}
		return channel != PayChannelEnum.WECHAT || StringUtils.hasText(revision.getWxPublicKeyId());
	}

	private boolean identityChanged(PayConfig target, PayConfig request) {
		return !java.util.Objects.equals(target.getType(), request.getType())
				|| !java.util.Objects.equals(target.getTerminalType(), request.getTerminalType())
				|| !java.util.Objects.equals(target.getAppId(), request.getAppId())
				|| !java.util.Objects.equals(target.getMchId(), request.getMchId());
	}

	private void validateConfig(PayConfig payConfig) {
		if (!StringUtils.hasText(payConfig.getType()) || !StringUtils.hasText(payConfig.getTerminalType())) {
			throw new ArynBusinessException("支付类型和支付端类型不能为空");
		}
		if (PayChannelEnum.fromCode(payConfig.getType()) == null) {
			throw new ArynBusinessException("不支持的支付类型");
		}
		if (!StringUtils.hasText(payConfig.getAppId()) || !StringUtils.hasText(payConfig.getMchId())) {
			throw new ArynBusinessException("应用ID和商户号不能为空");
		}
	}

	/**
	 * 旧字段不允许再通过普通配置接口写入，避免明文密钥重新进入数据库。
	 */
	private void rejectLegacySecrets(PayConfig payConfig) {
		if (StringUtils.hasText(payConfig.getMchKey()) || StringUtils.hasText(payConfig.getApiv3Key())
				|| StringUtils.hasText(payConfig.getKeyPath()) || StringUtils.hasText(payConfig.getPrivateKeyPath())
				|| StringUtils.hasText(payConfig.getPrivateCertPath())
				|| StringUtils.hasText(payConfig.getPublicKeyPath())) {
			throw new ArynBusinessException("敏感支付资料必须通过支付资料上传接口保存，普通配置接口不接受密钥或证书路径");
		}
	}

	private void assertUnique(String type, String terminalType, String excludeId) {
		LambdaQueryWrapper<PayConfig> query = Wrappers.<PayConfig>lambdaQuery()
			.eq(PayConfig::getType, type)
			.eq(PayConfig::getTerminalType, terminalType);
		if (StringUtils.hasText(excludeId)) {
			query.ne(PayConfig::getId, excludeId);
		}
		if (this.count(query) > 0) {
			throw new ArynBusinessException("该支付方式已存在");
		}
	}

}
