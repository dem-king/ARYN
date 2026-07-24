
package com.aryn.cloud.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.config.WxPayConfiguration;
import com.aryn.cloud.pay.handler.AbstractPayRefundOrderHandler;
import com.aryn.cloud.pay.service.IPayRefundOrderService;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("WECHAT_REFUND")
@RequiredArgsConstructor
public class WechatRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final IPayTradeOrderService tradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	@Override
	public Object doRefund(PayRefundOrder payRefundOrder) {
		// 查询支付单
		PayTradeOrder payTradeOrder = tradeOrderService.getOne(
				Wrappers.<PayTradeOrder>lambdaQuery().eq(PayTradeOrder::getOutTradeNo, payRefundOrder.getOutTradeNo())
					.last("LIMIT 1"));
		if (payTradeOrder == null || !CommonConstants.YES.equals(payTradeOrder.getPayStatus())
				|| payTradeOrder.getAmount() == null || payRefundOrder.getPayAmount() == null
				|| payTradeOrder.getAmount().compareTo(payRefundOrder.getPayAmount()) != 0
				|| !payRefundOrder.getUserId().equals(payTradeOrder.getUserId())) {
			throw new ArynBusinessException("原支付订单校验失败");
		}

		String notifyUrl = String.format("%s/%s/notify/refunds/wx/%s/%s", payRefundOrder.getNotifyUrl(),
				callbackPrefixProperties.getPay(), ArynTenantContextHolder.getTenantId(),
				payTradeOrder.getTerminalType());

		WxPayRefundV3Request.Amount amount = new WxPayRefundV3Request.Amount();
		amount.setTotal(payRefundOrder.getPayAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setRefund(payRefundOrder.getRefundAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setCurrency("CNY");
		WxPayRefundV3Request wxPayRefundV3Request = new WxPayRefundV3Request();
		wxPayRefundV3Request.setAmount(amount);
		wxPayRefundV3Request.setOutTradeNo(payRefundOrder.getOutTradeNo());
		wxPayRefundV3Request.setOutRefundNo(payRefundOrder.getRefundTradeNo());
		wxPayRefundV3Request.setNotifyUrl(notifyUrl);
		try {
			return WxPayConfiguration.wxPayService(payTradeOrder.getTerminalType()).refundV3(wxPayRefundV3Request);
		}
		catch (WxPayException e) {
			throw new ArynBusinessException(e.getReturnMsg());
		}
	}

	@Override
	public PayRefundOrder createRefundOrder(CreateRefundsReqDTO createRefundsReqDTO) {
		PayRefundOrder payRefundOrder = payRefundOrderService.getOne(Wrappers.<PayRefundOrder>lambdaQuery()
			.eq(PayRefundOrder::getRefundTradeNo, createRefundsReqDTO.getRefundTradeNo()).last("LIMIT 1"));
		if (null != payRefundOrder) {
			return payRefundOrder;
		}
		payRefundOrder = new PayRefundOrder();
		payRefundOrder.setRefundAmount(createRefundsReqDTO.getRefundAmount());
		payRefundOrder.setPayAmount(createRefundsReqDTO.getTotalAmount());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_1.getCode());
		payRefundOrder.setRefundTradeNo(createRefundsReqDTO.getRefundTradeNo());
		payRefundOrder.setNotifyUrl(createRefundsReqDTO.getNotifyUrl());
		payRefundOrder.setOutTradeNo(createRefundsReqDTO.getOutTradeNo());
		payRefundOrder.setExtra(createRefundsReqDTO.getExtra());
		payRefundOrder.setUserId(createRefundsReqDTO.getUserId());
		payRefundOrderService.save(payRefundOrder);
		return payRefundOrder;
	}

}
