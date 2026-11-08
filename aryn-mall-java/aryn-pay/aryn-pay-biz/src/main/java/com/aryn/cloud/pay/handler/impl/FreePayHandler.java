/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayTradeStatusEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.handler.AbstractPayOrderHandler;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayEventOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service("FREE_PAY")
@RequiredArgsConstructor
public class FreePayHandler extends AbstractPayOrderHandler {

	private static final String EVENT_PAY_SUCCESS = "PAY_SUCCESS";

	private final IPayTradeOrderService payTradeOrderService;

	private final PayEventOutboxService outboxService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Object pay(CreateOrderReqDTO createOrderReqDTO) {
		return super.pay(createOrderReqDTO);
	}

	@Override
	public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
		// 零元单与业务落账在同一个事务：状态更新和事件 outbox 原子提交
		payTradeOrder.setPayStatus(CommonConstants.YES);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PAID.name());
		payTradeOrder.setPaySuccessTime(LocalDateTime.now());
		payTradeOrderService.updateById(payTradeOrder);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put(PayConstants.OUT_TRADE_NO, payTradeOrder.getOutTradeNo());
		jsonObject.put(PayConstants.CHANNEL_ORDER_NO, payTradeOrder.getChannelOrderNo());
		jsonObject.put(PayConstants.PAY_SUCCESS_TIME, payTradeOrder.getPaySuccessTime());
		jsonObject.put(PayConstants.EXTRA_PARAMS, payTradeOrder.getExtra());
		jsonObject.put(PayConstants.TENANT_ID, ArynTenantContextHolder.getTenantId());
		outboxService.enqueue(ArynTenantContextHolder.getTenantId(), EVENT_PAY_SUCCESS,
				payTradeOrder.getOutTradeNo(), RocketMqConstants.PAY_NOTIFY_TOPIC, jsonObject.toJSONString());
		return PayPrepayResult.alreadyPaid(payTradeOrder.getOutTradeNo());
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
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setTenantId(ArynTenantContextHolder.getTenantId());
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PENDING.name());
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOpenId(createOrderReqDTO.getBuyerId());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setTradeType(PayTradeTypeEnum.FREE_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setChannelOrderNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrderService.save(payTradeOrder);

		return payTradeOrder;
	}

}
