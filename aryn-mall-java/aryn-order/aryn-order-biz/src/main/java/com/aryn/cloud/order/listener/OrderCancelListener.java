
package com.aryn.cloud.order.listener;

import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
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
		consumerGroup = RocketMqConstants.ORDER_CANCEL_TOPIC)
public class OrderCancelListener implements RocketMQListener<OrderConsumerDTO> {

	private final IOrderInfoService orderInfoService;

	@Override
	public void onMessage(OrderConsumerDTO orderConsumerDTO) {
		log.info("开始消费消息，消费信息为:{} ", orderConsumerDTO);
		ArynTenantContextHolder.removeTenantId();
		try {
			ArynTenantContextHolder.setTenantId(orderConsumerDTO.getTenantId());
			OrderInfo orderInfo = orderInfoService.getById(orderConsumerDTO.getOrderId());
			if (ObjectUtil.isNotNull(orderInfo) && CommonConstants.NO.equals(orderInfo.getPayStatus())
					&& OrderStatusEnum.WAITING_FOR_PAYMENT.getCode().equals(orderInfo.getStatus())) {
				orderInfoService.cancelOrder(orderInfo);
			}
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
