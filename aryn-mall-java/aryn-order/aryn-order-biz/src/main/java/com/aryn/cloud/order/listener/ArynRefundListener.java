
package com.aryn.cloud.order.listener;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
@RocketMQMessageListener(topic = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
		consumerGroup = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class ArynRefundListener implements RocketMQListener<String> {

	private final IOrderRefundService orderRefundService;

	private final IOrderItemService orderItemService;

	private final IOrderInfoService orderInfoService;

	private final RocketMQTemplate rocketMQTemplate;

	@Override
	public void onMessage(String message) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
				RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC, message, () -> doConsume(message));
	}

	private void doConsume(String message) {
		final JSONObject msg = JSONObject.parseObject(message);
		final String tenantId = msg.getString(PayConstants.TENANT_ID);
		if (!StringUtils.hasText(tenantId)) {
			log.warn("tenantId empty! ");
			return;
		}
		ArynTenantContextHolder.setTenantId(tenantId);
		final String refundTradeMo = msg.getString(PayConstants.REFUND_TRADE_NO);
		if (!StringUtils.hasText(refundTradeMo)) {
			log.warn("orderNo empty! ");
			return;
		}
		OrderRefund orderRefund = orderRefundService
			.getOne(Wrappers.<OrderRefund>lambdaQuery().eq(OrderRefund::getRefundTradeNo, refundTradeMo));
		if (null == orderRefund) {
			log.warn("order not found! orderNo: {}", refundTradeMo);
			return;
		}
		OrderItemEntity orderItemEntity = orderItemService.getById(orderRefund.getOrderItemId());
		if (null == orderItemEntity) {
			log.warn("order item not found! orderItemId: {}", orderRefund.getOrderItemId());
			return;
		}
		// 查询订单
		OrderInfo orderInfo = orderInfoService.getById(orderRefund.getOrderId());
		if (Objects.isNull(orderInfo)) {
			log.warn("order not found! orderId: {}", orderRefund.getOrderId());
			return;
		}
		if (OrderArrivalStatusEnum.REFUNDING.getCode().equals(orderRefund.getArrivalStatus())) {
			orderRefund.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
			orderRefundService.updateById(orderRefund);

			orderItemEntity.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
			orderItemService.updateById(orderItemEntity);
			// 查询所有子订单
			List<OrderItemEntity> orderItemEntityList = orderItemService.list(Wrappers.<OrderItemEntity>lambdaQuery()
				.eq(OrderItemEntity::getOrderId, orderRefund.getOrderId())
				.ne(OrderItemEntity::getId, orderItemEntity.getId())
				.ne(OrderItemEntity::getStatus, OrderItemStatusEnum.REFUNDED.getCode()));

			OrderRefundSuccessEvent orderRefundSuccessEvent = new OrderRefundSuccessEvent();
			BeanUtils.copyProperties(orderRefund, orderRefundSuccessEvent);
			orderRefundSuccessEvent.setPaymentType(orderInfo.getPaymentType());

			OrderItemRefundSuccessEvent orderItemRefundSuccessEvent = new OrderItemRefundSuccessEvent();
			BeanUtils.copyProperties(orderItemEntity, orderItemRefundSuccessEvent);
			orderRefundSuccessEvent.setOrderItem(orderItemRefundSuccessEvent);
			// 判断是否全部完成退款
			if (CollectionUtils.isEmpty(orderItemEntityList)) {
				// 取消订单
				OrderInfo info = new OrderInfo();
				info.setId(orderRefund.getOrderId());
				info.setStatus(OrderStatusEnum.CANCELED.getCode());
				orderInfoService.updateById(info);
				orderRefundSuccessEvent.setCouponUserId(orderInfo.getCouponUserId());
			}
			rocketMQTemplate.syncSend(RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
					new GenericMessage<>(orderRefundSuccessEvent), RocketMqConstants.TIME_OUT);

		}
	}

}
