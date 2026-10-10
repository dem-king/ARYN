package com.aryn.cloud.user.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.service.IRechargeOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 充值支付成功监听
 *
 * <p>与订单模块的 ArynPayListener 消费同一个 pay-notify-topic：支付中心不区分业务域，
 * 按 outTradeNo 能查到本模块待支付的充值单才由本监听器处理，否则直接返回，
 * 交由订单模块的监听器自行消费。
 *
 * @author 雨滴kian
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.PAY_NOTIFY_TOPIC,
		consumerGroup = "user-service-recharge-pay-group")
public class RechargePayNotifyListener implements RocketMQListener<String> {

	private final IRechargeOrderService rechargeOrderService;

	@Override
	public void onMessage(String message) {
		ArynTenantContextHolder.removeTenantId();
		final JSONObject msg = JSONObject.parseObject(message);
		final String tenantId = msg.getString(PayConstants.TENANT_ID);
		if (!StringUtils.hasText(tenantId)) {
			log.warn("tenantId empty! ");
			return;
		}
		ArynTenantContextHolder.setTenantId(tenantId);
		try {
			final String orderNo = msg.getString(PayConstants.OUT_TRADE_NO);
			if (!StringUtils.hasText(orderNo)) {
				log.warn("orderNo empty! ");
				return;
			}

			RechargeOrder order = rechargeOrderService.getOne(
					Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getOrderNo, orderNo));
			if (order == null) {
				// 不是充值单，属于订单域消息，忽略
				return;
			}

			// 已完成入账的重复投递直接忽略
			if ("1".equals(order.getPayStatus())) {
				log.info("充值订单已入账，忽略重复支付通知：orderNo={}", orderNo);
				return;
			}

			rechargeOrderService.paySuccess(orderNo, msg.getString(PayConstants.CHANNEL_ORDER_NO));
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
