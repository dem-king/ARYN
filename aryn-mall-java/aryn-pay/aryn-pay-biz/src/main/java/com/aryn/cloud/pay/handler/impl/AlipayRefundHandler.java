
package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
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
			sendRefundNotify(payRefundOrder);
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
			payRefundOrder.setRefundSuccessTime(LocalDateTime.now());
		}
		else if (queryRefundSucceeded(alipayClient, payRefundOrder)) {
			// fund_change=N 可能是退款成功后的重复请求，主动查询确认后不能停在「待确认」
			sendRefundNotify(payRefundOrder);
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
			payRefundOrder.setRefundSuccessTime(LocalDateTime.now());
		}
		else {
			// 渠道暂未确认，交给 AlipayRefundReconcileJob 继续查单收敛
			payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_4.getCode());
		}
		payRefundOrderService.updateById(payRefundOrder);

		return response;
	}

	/**
	 * 待确认退款自动核对：先以状态 4 -> 1 抢占，避免多实例重复通知；
	 * 查询确认成功后补发退款通知，查询失败或未成功则回退到待确认继续重试。
	 */
	public boolean reconcile(PayRefundOrder payRefundOrder) {
		boolean locked = payRefundOrderService.update(Wrappers.<PayRefundOrder>lambdaUpdate()
			.eq(PayRefundOrder::getId, payRefundOrder.getId())
			.eq(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_4.getCode())
			.set(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_1.getCode()));
		if (!locked) {
			return false;
		}
		try {
			PayTradeOrder payTradeOrder = tradeOrderService.getOne(
					Wrappers.<PayTradeOrder>lambdaQuery()
						.eq(PayTradeOrder::getOutTradeNo, payRefundOrder.getOutTradeNo())
						.last("LIMIT 1"));
			if (payTradeOrder == null) {
				resetToPending(payRefundOrder.getId());
				return false;
			}
			AlipayClient alipayClient = AliPayConfiguration.getAlipayClient(payTradeOrder.getTerminalType());
			if (!queryRefundSucceeded(alipayClient, payRefundOrder)) {
				resetToPending(payRefundOrder.getId());
				return false;
			}
			sendRefundNotify(payRefundOrder);
			return payRefundOrderService.update(Wrappers.<PayRefundOrder>lambdaUpdate()
				.eq(PayRefundOrder::getId, payRefundOrder.getId())
				.eq(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_1.getCode())
				.set(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_2.getCode())
				.set(PayRefundOrder::getRefundSuccessTime, LocalDateTime.now()));
		}
		catch (Exception exception) {
			log.warn("支付宝退款待确认自动核对失败 refundTradeNo={}", payRefundOrder.getRefundTradeNo(), exception);
			resetToPending(payRefundOrder.getId());
			return false;
		}
	}

	private void resetToPending(String id) {
		payRefundOrderService.update(Wrappers.<PayRefundOrder>lambdaUpdate()
			.eq(PayRefundOrder::getId, id)
			.eq(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_1.getCode())
			.set(PayRefundOrder::getRefundStatus, PayRefundOrderStatusEnum.STATUS_4.getCode()));
	}

	private boolean queryRefundSucceeded(AlipayClient alipayClient, PayRefundOrder payRefundOrder) {
		AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", payRefundOrder.getOutTradeNo());
		bizContent.put("out_request_no", payRefundOrder.getRefundTradeNo());
		request.setBizContent(bizContent.toString());
		try {
			AlipayTradeFastpayRefundQueryResponse response = alipayClient.certificateExecute(request);
			return response != null && response.isSuccess()
					&& "REFUND_SUCCESS".equals(response.getRefundStatus());
		}
		catch (AlipayApiException exception) {
			log.warn("支付宝退款查询失败 refundTradeNo={}", payRefundOrder.getRefundTradeNo(), exception);
			return false;
		}
	}

	@Override
	protected void notifyRefundSuccess(PayRefundOrder payRefundOrder) {
		sendRefundNotify(payRefundOrder);
	}

	private void sendRefundNotify(PayRefundOrder payRefundOrder) {
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
