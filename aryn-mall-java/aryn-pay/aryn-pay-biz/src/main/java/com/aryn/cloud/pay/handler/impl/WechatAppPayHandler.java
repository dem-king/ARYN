
package com.aryn.cloud.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.aryn.cloud.common.core.constant.CommonConstants;
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
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service("WX_APP_PAY")
@RequiredArgsConstructor
public class WechatAppPayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final PayCallbackUrlBuilder callbackUrlBuilder;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	@Override
	public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		PayResolvedIdentity identity = credentialResolver.resolveForOrder(tenantId, payTradeOrder);
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

		WxPayService wxPayService = clientFactory.wxPayService(identity);
		try {
			WxPayUnifiedOrderV3Result.AppResult appResult = wxPayService.createOrderV3(TradeTypeEnum.APP,
					wxPayUnifiedOrderV3Request);
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("appid", appResult.getAppid());
			params.put("partnerid", appResult.getPartnerId());
			params.put("prepayid", appResult.getPrepayId());
			params.put("package", appResult.getPackageValue());
			params.put("noncestr", appResult.getNoncestr());
			params.put("timestamp", appResult.getTimestamp());
			params.put("sign", appResult.getSign());
			return PayPrepayResult.of(PayChannelEnum.WECHAT.getCode(), PayTerminalTypeEnum.APP.getCode(),
					PayInvokeTypeEnum.WX_APP, payTradeOrder.getOutTradeNo(), params);
		}
		catch (WxPayException e) {
			throw new ArynBusinessException(e.getReturnMsg());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		PayTradeOrder payTradeOrder = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (null != payTradeOrder) {
			return payTradeOrder;
		}
		PayResolvedIdentity identity = credentialResolver.resolveNewPayment(PayChannelEnum.WECHAT.getCode(),
				PayTerminalTypeEnum.APP.getCode());
		payTradeOrder = new PayTradeOrder();
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PENDING.name());
		payTradeOrder.setTenantId(ArynTenantContextHolder.getTenantId());
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOpenId(createOrderReqDTO.getBuyerId());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setChannelMchId(identity.getMchId());
		payTradeOrder.setTradeType(PayTradeTypeEnum.WX_APP_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.APP.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setConfigRevisionId(identity.getRevisionId());
		payTradeOrderService.save(payTradeOrder);

		return payTradeOrder;
	}

}
