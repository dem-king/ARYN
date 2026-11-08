package com.aryn.cloud.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayInvokeTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTradeStatusEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.handler.AbstractPayOrderHandler;
import com.aryn.cloud.pay.service.AlipayMiniIdentityValidator;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayClientPlatform;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.user.api.dto.MiniAppBuyerIdentityVerifyDTO;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝小程序支付：alipay.trade.create 返回 tradeNo，由 my.tradePay 调起。
 *
 * <p>成功返回只代表交易已创建，绝不代表已支付；最终状态以服务端回调/查单为准。
 * 买家字段严格按身份版本冻结的 user_id/open_id 模式填写，并先经 user-api
 * 校验该身份确属当前租户、应用与商城用户。
 */
@Slf4j
@Service("ALI_JSAPI_PAY")
@RequiredArgsConstructor
public class AlipayJsApiPayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final PayCallbackUrlBuilder callbackUrlBuilder;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	@DubboReference
	private RemoteMiniAppTenantService remoteMiniAppTenantService;

	@Override
	public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		PayResolvedIdentity identity = credentialResolver.resolveForOrder(tenantId, payTradeOrder);
		MiniAppIdentityModeEnum mode = validateBuyerIdentity(identity, payTradeOrder.getOpenId(),
				payTradeOrder.getUserId(), tenantId);
		try {
			String notifyUrl = callbackUrlBuilder.buildAliPayNotifyUrl(payTradeOrder.getNotifyUrl(), tenantId,
					payTradeOrder.getConfigRevisionId());
			JSONObject bizContent = new JSONObject();
			bizContent.set("out_trade_no", payTradeOrder.getOutTradeNo());
			bizContent.set("total_amount", payTradeOrder.getAmount());
			bizContent.set("subject", payTradeOrder.getDescription());
			if (mode == MiniAppIdentityModeEnum.USER_ID) {
				bizContent.set("buyer_id", payTradeOrder.getOpenId());
			}
			else {
				bizContent.set("buyer_open_id", payTradeOrder.getOpenId());
			}
			AlipayTradeCreateRequest request = new AlipayTradeCreateRequest();
			request.setNotifyUrl(notifyUrl);
			request.setBizContent(bizContent.toString());
			AlipayClient client = clientFactory.alipayClient(identity);
			// 证书模式必须走 certificateExecute；普通 execute 在 alipayRootCertSN 非空时会直接报错
			AlipayTradeCreateResponse response = client.certificateExecute(request);
			if (response == null || !response.isSuccess() || !StringUtils.hasText(response.getTradeNo())) {
				String subCode = response == null ? null : response.getSubCode();
				log.warn("支付宝小程序创建交易失败, tenantId: {}, orderNo: {}, subCode: {}", tenantId,
						payTradeOrder.getOutTradeNo(), subCode);
				throw new ArynBusinessException("支付宝小程序支付下单失败");
			}
			// success/9000 前端结果不作为支付成功依据，订单状态仍由回调/查单推进
			return PayPrepayResult.of(PayChannelEnum.ALIPAY.getCode(), PayTerminalTypeEnum.MINI_PROGRAM.getCode(),
					PayInvokeTypeEnum.ALI_MINI, payTradeOrder.getOutTradeNo(),
					Map.of("tradeNo", response.getTradeNo()));
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception exception) {
			log.warn("支付宝小程序创建交易异常, tenantId: {}, orderNo: {}, msg: {}", tenantId,
					payTradeOrder.getOutTradeNo(), exception.getMessage());
			throw new ArynBusinessException("支付宝小程序支付下单失败");
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		PayTradeOrder existing = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (existing != null && !PayTradeTypeEnum.ALI_JSAPI_PAY.getName().equals(existing.getTradeType())) {
			throw new ArynBusinessException("支付订单交易类型不一致");
		}
		PayClientPlatform.requireAliMini(createOrderReqDTO.getClientPlatform());
		String tenantId = ArynTenantContextHolder.getTenantId();
		PayResolvedIdentity identity = existing != null
				? credentialResolver.resolveForOrder(tenantId, existing)
				: credentialResolver.resolveNewPayment(PayChannelEnum.ALIPAY.getCode(),
						PayTerminalTypeEnum.MINI_PROGRAM.getCode());
		validateBuyerIdentity(identity,
				existing != null ? existing.getOpenId() : createOrderReqDTO.getBuyerId(),
				existing != null ? existing.getUserId() : createOrderReqDTO.getUserId(), tenantId);
		if (existing != null) {
			return existing;
		}
		PayTradeOrder payTradeOrder = new PayTradeOrder();
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PENDING.name());
		payTradeOrder.setTenantId(ArynTenantContextHolder.getTenantId());
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOpenId(createOrderReqDTO.getBuyerId());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setChannelMchId(identity.getMchId());
		payTradeOrder.setTradeType(PayTradeTypeEnum.ALI_JSAPI_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setConfigRevisionId(identity.getRevisionId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

	private MiniAppIdentityModeEnum validateBuyerIdentity(PayResolvedIdentity identity, String buyerIdentity,
			String mallUserId, String tenantId) {
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(identity.getBuyerIdentityMode());
		if (mode == null) {
			throw new ArynBusinessException(403, "支付宝小程序买家身份模式未配置");
		}
		String normalized = AlipayMiniIdentityValidator.require(mode, buyerIdentity);
		if (!StringUtils.hasText(mallUserId) || !StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException(403, "支付宝小程序买家身份校验失败");
		}
		MiniAppBuyerIdentityVerifyDTO verify = new MiniAppBuyerIdentityVerifyDTO();
		verify.setTenantId(tenantId);
		verify.setPlatformType(OpenPlatformTypeEnum.ALI_MINI.getCode());
		verify.setAppId(identity.getAppId());
		verify.setIdentityMode(mode.getCode());
		verify.setBuyerIdentity(normalized);
		verify.setMallUserId(mallUserId);
		boolean matched;
		try {
			matched = remoteMiniAppTenantService.verifyBuyerIdentity(verify);
		}
		catch (RuntimeException exception) {
			log.warn("支付宝买家身份归属校验失败, tenantId: {}, orderUserId: {}, msg: {}", tenantId, mallUserId,
					exception.getMessage());
			throw new ArynBusinessException(403, "支付宝小程序买家身份校验失败");
		}
		if (!matched) {
			throw new ArynBusinessException(403, "支付宝买家身份与当前商城用户不匹配");
		}
		return mode;
	}

}
