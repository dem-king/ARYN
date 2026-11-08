
package com.aryn.cloud.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.constants.PayConstants;
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
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayClientPlatform;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.pay.service.WxMiniBuyerIdentityValidator;
import com.aryn.cloud.user.api.dto.MiniAppBuyerIdentityVerifyDTO;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service("WX_JSAPI_PAY")
@RequiredArgsConstructor
public class WechatJsApiPayHandler extends AbstractPayOrderHandler {

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
		validateBuyerIdentity(identity, payTradeOrder.getOpenId(), payTradeOrder.getUserId(), tenantId);
		String notifyUrl = callbackUrlBuilder.buildWxPayNotifyUrl(payTradeOrder.getNotifyUrl(), tenantId,
				payTradeOrder.getConfigRevisionId());
		WxPayUnifiedOrderV3Request wxPayUnifiedOrderV3Request = new WxPayUnifiedOrderV3Request();
		wxPayUnifiedOrderV3Request.setOutTradeNo(payTradeOrder.getOutTradeNo());
		wxPayUnifiedOrderV3Request.setDescription(payTradeOrder.getDescription());
		wxPayUnifiedOrderV3Request.setNotifyUrl(notifyUrl);
		// 订单金额 start
		WxPayUnifiedOrderV3Request.Amount amount = new WxPayUnifiedOrderV3Request.Amount();
		amount.setTotal(payTradeOrder.getAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setCurrency(PayConstants.CURRENCY);
		wxPayUnifiedOrderV3Request.setAmount(amount);
		// 订单金额 end
		// 支付者 start
		WxPayUnifiedOrderV3Request.Payer payer = new WxPayUnifiedOrderV3Request.Payer();
		payer.setOpenid(payTradeOrder.getOpenId());
		wxPayUnifiedOrderV3Request.setPayer(payer);
		// 支付者 end
		WxPayService wxPayService = clientFactory.wxPayService(identity);
		try {
			WxPayUnifiedOrderV3Result.JsapiResult jsapi = wxPayService.createOrderV3(TradeTypeEnum.JSAPI,
					wxPayUnifiedOrderV3Request);
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("timeStamp", jsapi.getTimeStamp());
			params.put("nonceStr", jsapi.getNonceStr());
			params.put("package", jsapi.getPackageValue());
			params.put("signType", jsapi.getSignType());
			params.put("paySign", jsapi.getPaySign());
			return PayPrepayResult.of(PayChannelEnum.WECHAT.getCode(), PayTerminalTypeEnum.MINI_PROGRAM.getCode(),
					PayInvokeTypeEnum.WX_MINI, payTradeOrder.getOutTradeNo(), params);
		}
		catch (WxPayException e) {
			throw new ArynBusinessException(e.getMessage());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		// 先查询：同一业务订单已有支付单时沿用其冻结身份，但仍校验本次调起来源平台
		PayTradeOrder existing = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (existing != null && !PayTradeTypeEnum.WX_JSAPI_PAY.getName().equals(existing.getTradeType())) {
			throw new ArynBusinessException("支付订单交易类型不一致");
		}
		PayClientPlatform.requireWxMini(createOrderReqDTO.getClientPlatform());
		String tenantId = ArynTenantContextHolder.getTenantId();
		PayResolvedIdentity identity = existing != null
				? credentialResolver.resolveForOrder(tenantId, existing)
				: credentialResolver.resolveNewPayment(PayChannelEnum.WECHAT.getCode(),
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
		payTradeOrder.setTradeType(PayTradeTypeEnum.WX_JSAPI_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setConfigRevisionId(identity.getRevisionId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

	/**
	 * 微信小程序 JSAPI 的 payer.openid 必须属于当前租户、微信应用与商城用户。
	 * 平台字段只做路由约束，该归属校验才是跨平台复用的实际防线。
	 */
	private void validateBuyerIdentity(PayResolvedIdentity identity, String buyerIdentity, String mallUserId,
			String tenantId) {
		String normalized = WxMiniBuyerIdentityValidator.require(buyerIdentity);
		if (!StringUtils.hasText(mallUserId) || !StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException(403, "微信小程序买家身份校验失败");
		}
		MiniAppBuyerIdentityVerifyDTO verify = new MiniAppBuyerIdentityVerifyDTO();
		verify.setTenantId(tenantId);
		verify.setPlatformType(OpenPlatformTypeEnum.WX_MA.getCode());
		verify.setAppId(identity.getAppId());
		verify.setIdentityMode(MiniAppIdentityModeEnum.OPEN_ID.getCode());
		verify.setBuyerIdentity(normalized);
		verify.setMallUserId(mallUserId);
		boolean matched;
		try {
			matched = remoteMiniAppTenantService.verifyBuyerIdentity(verify);
		}
		catch (RuntimeException exception) {
			log.warn("微信买家身份归属校验失败, tenantId: {}, orderUserId: {}, msg: {}", tenantId, mallUserId,
					exception.getMessage());
			throw new ArynBusinessException(403, "微信小程序买家身份校验失败");
		}
		if (!matched) {
			throw new ArynBusinessException(403, "微信小程序买家身份与当前商城用户不匹配");
		}
	}

}
