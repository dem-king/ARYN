
package com.aryn.cloud.order.listener;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
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
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@Component
@RocketMQMessageListener(topic = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC,
		consumerGroup = RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC)
public class ArynRefundListener implements RocketMQListener<String> {

	private final IOrderRefundService orderRefundService;

	private final IOrderItemService orderItemService;

	private final IOrderInfoService orderInfoService;

	private final RocketMQTemplate rocketMQTemplate;

	@Override
	@Transactional(rollbackFor = Exception.class)
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
			final String refundTradeMo = msg.getString(PayConstants.REFUND_TRADE_NO);
			if (!StringUtils.hasText(refundTradeMo)) {
				log.warn("orderNo empty! ");
				return;
			}
			OrderRefund orderRefund = orderRefundService
				.getOne(Wrappers.<OrderRefund>lambdaQuery().eq(OrderRefund::getRefundTradeNo, refundTradeMo));
			if (null == orderRefund) {
				log.warn("order not found! orderNo: " + refundTradeMo);
				return;
			}
			OrderItemEntity orderItemEntity = orderItemService.getById(orderRefund.getOrderItemId());
			if (null == orderItemEntity) {
				log.warn("order item not found! orderItemId: " + orderRefund.getOrderItemId());
				return;
			}
			OrderInfo orderInfo = orderInfoService.getById(orderRefund.getOrderId());
			if (Objects.isNull(orderInfo)) {
				log.warn("order not found! orderId: " + orderRefund.getOrderId());
				return;
			}
			if (OrderArrivalStatusEnum.REFUNDING.getCode().equals(orderRefund.getArrivalStatus())) {
				orderRefund.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
				if (!orderRefundService.updateById(orderRefund)) {
					throw new ArynBusinessException("退款状态更新失败，请重试");
				}
			}
			else if (!OrderArrivalStatusEnum.REFUND_SUCCESS.getCode().equals(orderRefund.getArrivalStatus())) {
				log.warn("refund status is not refundable, refundTradeNo: {}", refundTradeMo);
				return;
			}
			if (!OrderItemStatusEnum.REFUNDED.getCode().equals(orderItemEntity.getStatus())) {
				orderItemEntity.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
				if (!orderItemService.updateById(orderItemEntity)) {
					throw new ArynBusinessException("订单商品退款状态更新失败，请重试");
				}
			}

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
				if (!OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())) {
					OrderInfo info = new OrderInfo();
					info.setId(orderRefund.getOrderId());
					info.setStatus(OrderStatusEnum.CANCELED.getCode());
					if (!orderInfoService.updateById(info)) {
						throw new ArynBusinessException("订单退款完成状态更新失败，请重试");
					}
				}
				orderRefundSuccessEvent.setCouponUserId(orderInfo.getCouponUserId());
			}
			TransactionalMqUtils.sendAfterCommit(() -> rocketMQTemplate.syncSend(
				RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
				new GenericMessage<>(orderRefundSuccessEvent), RocketMqConstants.TIME_OUT));
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private BigDecimal refundBaseAmount(OrderRefund refund, OrderItemEntity orderItem) {
		BigDecimal refundAmount = refund.getRefundAmount() == null ? BigDecimal.ZERO : refund.getRefundAmount();
		BigDecimal freightAmount = orderItem.getFreightPrice() == null ? BigDecimal.ZERO : orderItem.getFreightPrice();
		return refundAmount.subtract(freightAmount).max(BigDecimal.ZERO);
	}

}
