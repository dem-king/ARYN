
package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.handler.AbstractPayRefundOrderHandler;
import com.aryn.cloud.pay.service.IPayRefundOrderService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service("FREE_REFUND")
@RequiredArgsConstructor
public class FreeRefundHandler extends AbstractPayRefundOrderHandler {

	private final IPayRefundOrderService payRefundOrderService;

	private final RocketMQTemplate rocketMQTemplate;

	@Override
	public Object doRefund(PayRefundOrder payRefundOrder) {
		payRefundOrder.setRefundSuccessTime(LocalDateTime.now());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
		payRefundOrderService.updateById(payRefundOrder);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(PayConstants.EXTRA_PARAMS, payRefundOrder.getExtra());
		jsonObject.put(PayConstants.REFUND_TRADE_NO, payRefundOrder.getRefundTradeNo());
		jsonObject.put(PayConstants.TENANT_ID, ArynTenantContextHolder.getTenantId());
		SendResult sendResult = rocketMQTemplate.syncSend(RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
				new GenericMessage<>(jsonObject), RocketMqConstants.TIME_OUT);
		if (sendResult == null || !SendStatus.SEND_OK.equals(sendResult.getSendStatus())) {
			throw new IllegalStateException("退款状态通知发送失败");
		}
		return payRefundOrder;
	}

	@Override
	public PayRefundOrder createRefundOrder(CreateRefundsReqDTO createRefundsReqDTO) {
		PayRefundOrder payRefundOrder = payRefundOrderService.getOne(Wrappers.<PayRefundOrder>lambdaQuery()
			.eq(PayRefundOrder::getRefundTradeNo, createRefundsReqDTO.getRefundTradeNo()).last("LIMIT 1"));
		if (null != payRefundOrder) {
			return payRefundOrder;
		}
		payRefundOrder = new PayRefundOrder();
		payRefundOrder.setUserId(createRefundsReqDTO.getUserId());
		payRefundOrder.setRefundAmount(createRefundsReqDTO.getRefundAmount());
		payRefundOrder.setPayAmount(createRefundsReqDTO.getTotalAmount());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_1.getCode());
		payRefundOrder.setRefundTradeNo(createRefundsReqDTO.getRefundTradeNo());
		payRefundOrder.setNotifyUrl(createRefundsReqDTO.getNotifyUrl());
		payRefundOrder.setOutTradeNo(createRefundsReqDTO.getOutTradeNo());
		payRefundOrder.setChannelRefundNo(createRefundsReqDTO.getRefundTradeNo());
		payRefundOrder.setExtra(createRefundsReqDTO.getExtra());
		payRefundOrderService.save(payRefundOrder);
		return payRefundOrder;
	}

}
