
package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.config.AliPayConfiguration;
import com.aryn.cloud.pay.handler.AbstractPayRefundOrderHandler;
import com.aryn.cloud.pay.service.IPayRefundOrderService;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

@Service("ALIPAY_REFUND")
@RequiredArgsConstructor
public class AlipayRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final RocketMQTemplate rocketMQTemplate;

	private final IPayTradeOrderService tradeOrderService;

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

		AlipayClient alipayClient = null;
		try {
			alipayClient = AliPayConfiguration.getAlipayClient(payTradeOrder.getTerminalType());
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("refund_amount", payRefundOrder.getRefundAmount());
		bizContent.put("out_trade_no", payRefundOrder.getOutTradeNo());
		bizContent.put("out_request_no", payRefundOrder.getRefundTradeNo());
		request.setBizContent(bizContent.toString());

		AlipayTradeRefundResponse response = null;
		try {
			response = alipayClient.certificateExecute(request);
		}
		catch (AlipayApiException e) {
			throw new ArynBusinessException(e.getErrMsg());
		}
		if (!response.isSuccess()) {
			throw new ArynBusinessException(response.getMsg() + response.getSubMsg());
		}
		if ("Y".equals(response.getFundChange())) {
			// 退款成功发送mq消息
			// rocketmq 通知
			JSONObject jsonObject = new JSONObject();
			jsonObject.put(PayConstants.EXTRA_PARAMS, payRefundOrder.getExtra());
			jsonObject.put(PayConstants.REFUND_TRADE_NO, payRefundOrder.getRefundTradeNo());
			jsonObject.put(PayConstants.TENANT_ID, ArynTenantContextHolder.getTenantId());
			SendResult sendResult = rocketMQTemplate.syncSend(RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
					new GenericMessage<>(jsonObject), RocketMqConstants.TIME_OUT);
			if (sendResult == null || !SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
				throw new IllegalStateException("退款状态通知发送失败");
			}
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
		}
		else {
			// 退款接口返回 fund_change=N
			// 不代表交易没有退款，只是代表该次接口请求没有资金变动，此时需使用退款查询接口进行查询判断，该笔交易是退款失败，还是退款成功后重复操作导致。
			// 状态更改成待确认 定时任务跑批处理
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_4.getCode());
		}
		payRefundOrderService.updateById(payRefundOrder);

		return response;
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
