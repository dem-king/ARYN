package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.api.vo.PayCapabilityVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付能力读取：只下发已实现、已启用且本地材料齐备的渠道。
 */
@Service
@RequiredArgsConstructor
public class PayCapabilityService {

	private final PayConfigMapper payConfigMapper;

	private final PayCredentialResolver credentialResolver;

	@DubboReference
	private RemoteMiniAppTenantService remoteMiniAppTenantService;

	public List<PayCapabilityVO> query(String terminalType) {
		return query(terminalType, null);
	}

	/**
	 * 小程序端只能下发本平台可调起的渠道：微信小程序不展示支付宝小程序支付，
	 * 支付宝小程序不展示微信小程序支付；App/H5 不额外限制。
	 */
	public List<PayCapabilityVO> query(String terminalType, String platformType) {
		List<PayCapabilityVO> result = new ArrayList<>();
		for (PayTradeTypeEnum tradeType : PayTradeTypeEnum.values()) {
			if (terminalType != null && tradeType.getTerminal() != null
					&& !terminalType.equals(tradeType.getTerminal().getCode())) {
				continue;
			}
			if (!supportsPlatform(tradeType, platformType)) {
				continue;
			}
			result.add(capability(tradeType));
		}
		return result;
	}

	private boolean supportsPlatform(PayTradeTypeEnum tradeType, String platformType) {
		String platform = PayClientPlatform.normalize(platformType);
		if (!StringUtils.hasText(platform)) {
			return true;
		}
		if (OpenPlatformTypeEnum.WX_MA.getCode().equals(platform)) {
			return tradeType.getChannel() != PayChannelEnum.ALIPAY;
		}
		if (OpenPlatformTypeEnum.ALI_MINI.getCode().equals(platform)) {
			return tradeType.getChannel() != PayChannelEnum.WECHAT;
		}
		return true;
	}

	private PayCapabilityVO capability(PayTradeTypeEnum tradeType) {
		PayCapabilityVO vo = new PayCapabilityVO();
		vo.setTradeType(tradeType.getName());
		vo.setChannel(tradeType.getChannel().getCode());
		vo.setTerminalType(tradeType.getTerminal() == null ? null : tradeType.getTerminal().getCode());
		vo.setInvokeType(tradeType.getInvokeType().name());
		if (tradeType.getChannel() == PayChannelEnum.FREE) {
			vo.setAvailable(true);
			return vo;
		}
		if (!tradeType.isImplemented()) {
			vo.setAvailable(false);
			vo.setReason("该支付场景尚未开放");
			return vo;
		}
		var config = payConfigMapper.selectConfig(tradeType.getChannel().getCode(),
				tradeType.getTerminal().getCode());
		if (config == null) {
			vo.setAvailable(false);
			vo.setReason("未配置支付渠道");
			return vo;
		}
		if (!"1".equals(config.getEnabled()) || config.getActiveRevisionId() == null
				|| config.getActiveRevisionId().isBlank()) {
			vo.setAvailable(false);
			vo.setReason("支付渠道未启用");
			return vo;
		}
		try {
			PayResolvedIdentity identity = credentialResolver.resolveNewPayment(config.getId());
			vo.setAvailable(identity.hasRequiredMaterials());
			if (!vo.isAvailable()) {
				vo.setReason("支付渠道材料不完整");
				return vo;
			}
			if (tradeType == PayTradeTypeEnum.ALI_JSAPI_PAY) {
				String reason = alipayMiniBindingBlocker(identity);
				if (reason != null) {
					vo.setAvailable(false);
					vo.setReason(reason);
				}
			}
		}
		catch (ArynBusinessException exception) {
			vo.setAvailable(false);
			vo.setReason(exception.getMsg());
		}
		return vo;
	}

	/**
	 * 支付宝小程序能力还必须满足：支付身份已冻结买家模式，且三方账号绑定
	 * 与支付身份同租户、同应用、同模式。任何一项缺失都不下发能力。
	 */
	private String alipayMiniBindingBlocker(PayResolvedIdentity identity) {
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(identity.getBuyerIdentityMode());
		if (mode == null) {
			return "支付宝小程序买家身份模式未配置";
		}
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId)) {
			return "缺少租户上下文";
		}
		try {
			MiniAppTenantBindingDTO binding = remoteMiniAppTenantService.requireBinding(identity.getAppId(), tenantId,
					OpenPlatformTypeEnum.ALI_MINI.getCode());
			if (binding == null || !Boolean.TRUE.equals(binding.getReady())
					|| !OpenPlatformTypeEnum.ALI_MINI.getCode().equals(binding.getPlatformType())
					|| !mode.getCode().equals(binding.getIdentityMode())) {
				return "支付宝小程序三方账号身份模式不一致";
			}
			return null;
		}
		catch (RuntimeException exception) {
			return "支付宝小程序三方账号配置未就绪";
		}
	}

}
