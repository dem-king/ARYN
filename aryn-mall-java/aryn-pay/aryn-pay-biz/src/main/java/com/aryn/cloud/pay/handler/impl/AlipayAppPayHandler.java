/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.pay.handler.impl;

import cn.hutool.json.JSONObject;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
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

@Service("ALI_APP_PAY")
@RequiredArgsConstructor
public class AlipayAppPayHandler extends AbstractPayOrderHandler {

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
			AlipayTradeCreateRequest alipayTradeCreateRequest = new AlipayTradeCreateRequest();
			alipayTradeCreateRequest.setNotifyUrl(notifyUrl);
			bizContent.set("buyer_id", payTradeOrder.getOpenId());
			alipayTradeCreateRequest.setBizContent(bizContent.toString());
			AlipayTradeCreateResponse alipayTradeCreateResponse = alipayClient
				.certificateExecute(alipayTradeCreateRequest);
			if (!alipayTradeCreateResponse.isSuccess()) {
				throw new ArynBusinessException(
						alipayTradeCreateResponse.getMsg() + alipayTradeCreateResponse.getSubMsg());
			}
			return alipayTradeCreateResponse;
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
		payTradeOrder.setTradeType(PayTradeTypeEnum.ALI_APP_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.APP.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

}
