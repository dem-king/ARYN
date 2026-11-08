
package com.aryn.cloud.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
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
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service("ALI_H5_PAY")
@RequiredArgsConstructor
public class AlipayH5PayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final PayCallbackUrlBuilder callbackUrlBuilder;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	@Override
	public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
		try {
			String tenantId = ArynTenantContextHolder.getTenantId();
			PayResolvedIdentity identity = credentialResolver.resolveForOrder(tenantId, payTradeOrder);
			String notifyUrl = callbackUrlBuilder.buildAliPayNotifyUrl(payTradeOrder.getNotifyUrl(), tenantId,
					payTradeOrder.getConfigRevisionId());
			AlipayClient alipayClient = clientFactory.alipayClient(identity);
			JSONObject bizContent = new JSONObject();
			bizContent.set("out_trade_no", payTradeOrder.getOutTradeNo());
			bizContent.set("total_amount", payTradeOrder.getAmount());
			bizContent.set("subject", payTradeOrder.getDescription());
			bizContent.set("product_code", "QUICK_WAP_WAY");
			bizContent.set("quit_url", payTradeOrder.getQuitUrl());
			AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
			request.setNotifyUrl(notifyUrl);
			request.setReturnUrl(payTradeOrder.getReturnUrl());
			request.setBizContent(bizContent.toString());
			// GET 方式返回跳转 URL；POST 默认返回表单，不能当 URL 使用
			String redirectUrl = alipayClient.pageExecute(request, "GET").getBody();
			return PayPrepayResult.of(PayChannelEnum.ALIPAY.getCode(), PayTerminalTypeEnum.H5.getCode(),
					PayInvokeTypeEnum.REDIRECT, payTradeOrder.getOutTradeNo(),
					Map.of("redirectUrl", redirectUrl));
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception e) {
			throw new ArynBusinessException(e.getMessage());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		PayTradeOrder payTradeOrder = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (null != payTradeOrder) {
			return payTradeOrder;
		}
		PayResolvedIdentity identity = credentialResolver.resolveNewPayment(PayChannelEnum.ALIPAY.getCode(),
				PayTerminalTypeEnum.H5.getCode());
		payTradeOrder = new PayTradeOrder();
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PENDING.name());
		payTradeOrder.setTenantId(ArynTenantContextHolder.getTenantId());
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOpenId(createOrderReqDTO.getBuyerId());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setChannelMchId(identity.getMchId());
		payTradeOrder.setTradeType(PayTradeTypeEnum.ALI_H5_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setReturnUrl(createOrderReqDTO.getReturnUrl());
		payTradeOrder.setQuitUrl(createOrderReqDTO.getQuitUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.H5.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setConfigRevisionId(identity.getRevisionId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

}
