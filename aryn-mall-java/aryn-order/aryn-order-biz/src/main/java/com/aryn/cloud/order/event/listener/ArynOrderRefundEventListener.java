
package com.aryn.cloud.order.event.listener;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.event.ArynOrderRefundEvent;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderRefundEventListener {

	private final IOrderRefundService orderRefundService;

	private final IOrderItemService orderItemService;

	private final IOrderInfoService orderInfoService;

	private final RocketMQTemplate rocketMQTemplate;

	/**
	 * 退款单 状态更改
	 * @param event
	 */
	@EventListener(ArynOrderRefundEvent.class)
	@Transactional(rollbackFor = Exception.class)
	public void refundStatusEventListener(ArynOrderRefundEvent event) {
		// 退款单信息
		OrderRefund orderRefund = event.getOrderRefund();
		if (null == orderRefund) {
			log.warn("orderRefund not found!");
			return;
		}
		orderRefund.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
		if (!orderRefundService.updateById(orderRefund)) {
			throw new ArynBusinessException("退款状态更新失败，请重试");
		}

		OrderItemEntity orderItemEntity = event.getOrderItemEntity();
		orderItemEntity.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
		if (!orderItemService.updateById(orderItemEntity)) {
			throw new ArynBusinessException("订单商品退款状态更新失败，请重试");
		}

		OrderInfo orderInfo = event.getOrderInfo();

		// 查询所有子订单
		List<OrderItemEntity> orderItemEntityList = orderItemService.list(Wrappers.<OrderItemEntity>lambdaQuery()
			.eq(OrderItemEntity::getOrderId, orderRefund.getOrderId())
			.ne(OrderItemEntity::getId, orderItemEntity.getId())
			.ne(OrderItemEntity::getStatus, OrderItemStatusEnum.REFUNDED.getCode()));

		OrderRefundSuccessEvent orderRefundSuccessEvent = new OrderRefundSuccessEvent();
		BeanUtils.copyProperties(orderRefund, orderRefundSuccessEvent);
		orderRefundSuccessEvent.setPaymentType(orderInfo.getPaymentType());
		orderRefundSuccessEvent.setRefundNo(orderRefund.getId());
		orderRefundSuccessEvent.setRefundBaseAmount(refundBaseAmount(orderRefund, orderItemEntity));

		OrderItemRefundSuccessEvent orderItemRefundSuccessEvent = new OrderItemRefundSuccessEvent();
		BeanUtils.copyProperties(orderItemEntity, orderItemRefundSuccessEvent);
		orderRefundSuccessEvent.setOrderItem(orderItemRefundSuccessEvent);
		// 判断是否全部完成退款
		if (CollectionUtils.isEmpty(orderItemEntityList)) {
			// 取消订单
			OrderInfo info = new OrderInfo();
			info.setId(orderRefund.getOrderId());
			info.setStatus(OrderStatusEnum.CANCELED.getCode());
			if (!orderInfoService.updateById(info)) {
				throw new ArynBusinessException("订单退款完成状态更新失败，请重试");
			}
			orderRefundSuccessEvent.setCouponUserId(orderInfo.getCouponUserId());
		}
		TransactionalMqUtils.sendAfterCommit(() -> rocketMQTemplate.syncSend(
			RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
			new GenericMessage<>(orderRefundSuccessEvent), RocketMqConstants.TIME_OUT));
	}

	private BigDecimal refundBaseAmount(OrderRefund refund, OrderItemEntity orderItem) {
		BigDecimal refundAmount = refund.getRefundAmount() == null ? BigDecimal.ZERO : refund.getRefundAmount();
		BigDecimal freightAmount = orderItem.getFreightPrice() == null ? BigDecimal.ZERO : orderItem.getFreightPrice();
		return refundAmount.subtract(freightAmount).max(BigDecimal.ZERO);
	}

}
