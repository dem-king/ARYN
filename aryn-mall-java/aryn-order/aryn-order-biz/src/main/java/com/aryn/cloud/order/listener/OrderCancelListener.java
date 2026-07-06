
package com.aryn.cloud.order.listener;

import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.service.IOrderInfoService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单取消消费者 特别注意，这里topic必须和生产者的一致
 *
 * @author 雨滴kian
 * @date 2022/8/6
 */
@Slf4j
@AllArgsConstructor
@Component
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_CANCEL_TOPIC,
		consumerGroup = RocketMqConstants.ORDER_CANCEL_TOPIC,
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class OrderCancelListener implements RocketMQListener<OrderConsumerDTO> {

	private final IOrderInfoService orderInfoService;

	@Override
	public void onMessage(OrderConsumerDTO orderConsumerDTO) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.ORDER_CANCEL_TOPIC,
				RocketMqConstants.ORDER_CANCEL_TOPIC, orderConsumerDTO, () -> doConsume(orderConsumerDTO));
	}

	private void doConsume(OrderConsumerDTO orderConsumerDTO) {
		ArynTenantContextHolder.setTenantId(orderConsumerDTO.getTenantId());
		OrderInfo orderInfo = orderInfoService.getById(orderConsumerDTO.getOrderId());
		// 只有待支付的订单能取消
		if (ObjectUtil.isNotNull(orderInfo) && CommonConstants.NO.equals(orderInfo.getPayStatus())) {
			orderInfoService.cancelOrder(orderInfo);
		}
	}

}
