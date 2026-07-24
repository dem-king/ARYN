
package com.aryn.cloud.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.config.AliPayConfiguration;
import com.aryn.cloud.pay.handler.AbstractPayOrderHandler;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("ALI_H5_PAY")
@RequiredArgsConstructor
public class AlipayH5PayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	@Override
	public Object doPay(PayTradeOrder payTradeOrder) {
		try {
			String notifyUrl = String.format("%s/%s/notify/alipay/%s/%s", payTradeOrder.getNotifyUrl(),
					callbackPrefixProperties.getPay(), ArynTenantContextHolder.getTenantId(),
					payTradeOrder.getTerminalType());
			AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(payTradeOrder.getTerminalType());
			JSONObject bizContent = new JSONObject();
			/****** 必传参数 ******/
			bizContent.set("out_trade_no", payTradeOrder.getOutTradeNo());
			bizContent.set("total_amount", payTradeOrder.getAmount());
			bizContent.set("subject", payTradeOrder.getDescription());
			AlipayTradeWapPayRequest alipayTradeWapPayRequest = new AlipayTradeWapPayRequest();
			// 异步接收地址，仅支持http/https，公网可访问
			alipayTradeWapPayRequest.setNotifyUrl(notifyUrl);
			// 同步跳转地址，仅支持http/https
			alipayTradeWapPayRequest.setReturnUrl(payTradeOrder.getReturnUrl());
			// 用户付款中途退出返回商户网站的地址
			bizContent.set("quit_url", payTradeOrder.getQuitUrl());
			alipayTradeWapPayRequest.setBizContent(bizContent.toString());
			AlipayTradeWapPayResponse alipayTradeWapPayResponse = alipayClient
				.certificateExecute(alipayTradeWapPayRequest);
			if (!alipayTradeWapPayResponse.isSuccess()) {
				throw new ArynBusinessException(
						alipayTradeWapPayResponse.getMsg() + alipayTradeWapPayResponse.getSubMsg());
			}
			return alipayTradeWapPayResponse;
		}
		catch (Exception e) {
			throw new ArynBusinessException(e.getMessage());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		// 先查询
		PayTradeOrder payTradeOrder = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (null != payTradeOrder) {
			return payTradeOrder;
		}
		payTradeOrder = new PayTradeOrder();
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOpenId(createOrderReqDTO.getBuyerId());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setTradeType(PayTradeTypeEnum.ALI_H5_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setReturnUrl(createOrderReqDTO.getReturnUrl());
		payTradeOrder.setQuitUrl(createOrderReqDTO.getQuitUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.H5.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

}
