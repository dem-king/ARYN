
package com.aryn.cloud.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.config.WxPayConfiguration;
import com.aryn.cloud.pay.handler.AbstractPayOrderHandler;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("WX_JSAPI_PAY")
@RequiredArgsConstructor
public class WechatJsApiPayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	@Override
	public Object doPay(PayTradeOrder payTradeOrder) {
		String notifyUrl = String.format("%s/%s/notify/wx/%s/%s", payTradeOrder.getNotifyUrl(),
				callbackPrefixProperties.getPay(), ArynTenantContextHolder.getTenantId(),
				payTradeOrder.getTerminalType());
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
		WxPayService wxPayService = WxPayConfiguration.wxPayService(payTradeOrder.getTerminalType());
		try {
			return wxPayService.createOrderV3(TradeTypeEnum.JSAPI, wxPayUnifiedOrderV3Request);
		}
		catch (WxPayException e) {
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
		payTradeOrder.setTradeType(PayTradeTypeEnum.WX_JSAPI_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.MINI_PROGRAM.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

}
