package com.aryn.cloud.pay.dubbo;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.AlipayAuthCodeExchangeReqDTO;
import com.aryn.cloud.pay.api.dto.AlipayMiniIdentityDTO;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import com.aryn.cloud.pay.api.remote.RemoteAlipayAuthService;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import com.aryn.cloud.pay.service.AlipayMiniIdentityValidator;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付宝小程序授权码交换。
 *
 * <p>应用身份与私钥来自支付身份版本的加密材料；买家身份模式必须同时配置在
 * social_account 与 pay_config_revision，且两者一致。任何缺失/不一致都 fail-closed。
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteAlipayAuthServiceImpl implements RemoteAlipayAuthService {

	private final PayConfigMapper payConfigMapper;

	private final PayConfigRevisionMapper revisionMapper;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	@Override
	public AlipayMiniIdentityDTO exchangeAuthCode(AlipayAuthCodeExchangeReqDTO request) {
		if (request == null || !StringUtils.hasText(request.getTenantId())
				|| !StringUtils.hasText(request.getAppId()) || !StringUtils.hasText(request.getAuthCode())) {
			throw new ArynBusinessException(403, "支付宝授权码交换参数不完整");
		}
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(request.getIdentityMode());
		if (mode == null) {
			throw new ArynBusinessException(403, "支付宝小程序身份模式未配置");
		}
		String tenantId = request.getTenantId().trim();
		PayConfig config = payConfigMapper.selectByTenantRoute(tenantId, PayChannelEnum.ALIPAY.getCode(),
				PayTerminalTypeEnum.MINI_PROGRAM.getCode());
		if (config == null || !StringUtils.hasText(config.getActiveRevisionId())) {
			throw new ArynBusinessException(403, "支付宝小程序支付身份未配置");
		}
		PayConfigRevision revision = revisionMapper.selectByTenantAndId(tenantId, config.getActiveRevisionId());
		if (revision == null || !config.getId().equals(revision.getConfigId())
				|| !PayChannelEnum.ALIPAY.getCode().equals(revision.getChannel())
				|| !PayTerminalTypeEnum.MINI_PROGRAM.getCode().equals(revision.getTerminalType())) {
			throw new ArynBusinessException(403, "支付宝小程序支付身份未配置");
		}
		if (!request.getAppId().trim().equals(revision.getAppId())) {
			throw new ArynBusinessException(403, "支付宝小程序应用与支付身份不一致");
		}
		MiniAppIdentityModeEnum configuredMode = MiniAppIdentityModeEnum.fromCode(revision.getBuyerIdentityMode());
		if (configuredMode == null) {
			throw new ArynBusinessException(403, "支付宝小程序支付身份未配置买家身份模式");
		}
		if (configuredMode != mode) {
			throw new ArynBusinessException(403, "支付宝小程序身份模式与支付身份不一致");
		}
		PayResolvedIdentity identity = credentialResolver.buildIdentity(tenantId, config, revision);
		if (!identity.hasRequiredMaterials()) {
			throw new ArynBusinessException(403, "支付宝小程序支付材料不完整");
		}
		AlipaySystemOauthTokenResponse response = requestToken(identity, request.getAuthCode().trim());
		String buyerIdentity = AlipayMiniIdentityValidator.select(mode, response.getUserId(), response.getOpenId());
		AlipayMiniIdentityDTO result = new AlipayMiniIdentityDTO();
		result.setTenantId(tenantId);
		result.setAppId(revision.getAppId());
		result.setIdentityMode(mode.getCode());
		result.setIdentity(buyerIdentity);
		return result;
	}

	private AlipaySystemOauthTokenResponse requestToken(PayResolvedIdentity identity, String authCode) {
		try {
			AlipaySystemOauthTokenRequest request = new AlipaySystemOauthTokenRequest();
			request.setGrantType("authorization_code");
			request.setCode(authCode);
			AlipayClient client = clientFactory.alipayClient(identity);
			// 证书模式必须走 certificateExecute；普通 execute 在 alipayRootCertSN 非空时会直接报错
			AlipaySystemOauthTokenResponse response = client.certificateExecute(request);
			if (response == null || !response.isSuccess()) {
				String subCode = response == null ? null : response.getSubCode();
				log.warn("支付宝授权码交换失败, tenantId: {}, appId: {}, subCode: {}", identity.getTenantId(),
						identity.getAppId(), subCode);
				throw new ArynBusinessException(403, "支付宝授权码校验失败");
			}
			return response;
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception exception) {
			log.warn("支付宝授权码交换异常, tenantId: {}, appId: {}, msg: {}", identity.getTenantId(),
					identity.getAppId(), exception.getMessage());
			throw new ArynBusinessException(403, "支付宝授权码校验失败");
		}
	}

}
