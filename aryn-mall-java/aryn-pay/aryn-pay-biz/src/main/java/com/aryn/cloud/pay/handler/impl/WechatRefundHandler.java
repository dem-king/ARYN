
package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.config.WxPayConfiguration;
import com.aryn.cloud.pay.handler.AbstractPayRefundOrderHandler;
import com.aryn.cloud.pay.service.IPayRefundOrderService;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("WECHAT_REFUND")
@RequiredArgsConstructor
public class WechatRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final IPayTradeOrderService tradeOrderService;

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final RocketMQTemplate rocketMQTemplate;

	@Override
	protected void notifyRefundSuccess(PayRefundOrder payRefundOrder) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(PayConstants.EXTRA_PARAMS, payRefundOrder.getExtra());
		jsonObject.put(PayConstants.REFUND_TRADE_NO, payRefundOrder.getRefundTradeNo());
		jsonObject.put(PayConstants.TENANT_ID, ArynTenantContextHolder.getTenantId());
		SendResult sendResult = rocketMQTemplate.syncSend(RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
				new GenericMessage<>(jsonObject), RocketMqConstants.TIME_OUT);
		if (sendResult == null || !SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
			throw new IllegalStateException("退款状态通知发送失败");
		}
	}

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
			WxPayRefundV3Result result = WxPayConfiguration.wxPayService(payTradeOrder.getTerminalType())
				.refundV3(wxPayRefundV3Request);
			// 微信同步返回成功时立即落账并通知，回调丢失也不会让订单侧一直停在退款中
			if (result != null && "SUCCESS".equals(result.getStatus())) {
				payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
				payRefundOrder.setChannelRefundNo(result.getRefundId());
				payRefundOrderService.updateById(payRefundOrder);
				notifyRefundSuccess(payRefundOrder);
			}
			return result;
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
