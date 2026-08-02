
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.event.ArynOrderPayEvent;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 支付成功事件监听
 *
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderPayEventListener {

	private final IOrderInfoService orderInfoService;

	private final IOrderItemService orderItemService;

	private final OrderPaySuccessNotifier orderPaySuccessNotifier;

	private final IDeliveryTaskService deliveryTaskService;

	/**
	 * 订单状态修改
	 * @param event
	 */
	@EventListener(ArynOrderPayEvent.class)
	@Transactional(rollbackFor = Exception.class)
	public void hxPayEventListener(ArynOrderPayEvent event) {
		// 获取订单信息
		OrderInfo orderInfo = event.getOrder();
		if (null == orderInfo) {
			log.warn("order not found!");
			return;
		}

		// 商城配送方式：支付后即等待配送员派单，订单状态仍为待发货
		String targetStatus;
		if (MallOrderConstants.DELIVERY_WAY_2.equals(orderInfo.getDeliveryWay())) {
			targetStatus = OrderStatusEnum.WAITING_FOR_RECEIPT.getCode();
		}
		else if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())) {
			targetStatus = OrderStatusEnum.WAITING_FOR_DELIVERY.getCode();
		}
		else {
			targetStatus = OrderStatusEnum.WAITING_FOR_DELIVERY.getCode();
		}
		if (!orderInfoService.update(Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
			.set(OrderInfo::getStatus, targetStatus)
			.set(OrderInfo::getPayStatus, CommonConstants.YES)
			.set(OrderInfo::getPaymentTime, orderInfo.getPaymentTime())
			.set(OrderInfo::getPaymentType, orderInfo.getPaymentType())
			.set(OrderInfo::getTransactionId, orderInfo.getTransactionId()))) {
			throw new ArynBusinessException("订单支付状态更新失败，请重试");
		}
		orderInfo.setStatus(targetStatus);
		orderInfo.setPayStatus(CommonConstants.YES);

		List<OrderItemEntity> orderItemEntityList = event.getOrderItemEntityList();
		orderItemEntityList.forEach(orderItem -> {
			if (orderInfo.getDeliveryWay().equals(MallOrderConstants.DELIVERY_WAY_2)) {
				orderItem.setStatus(OrderItemStatusEnum.SHIPPED.getCode());
			}
			else {
				orderItem.setStatus(OrderItemStatusEnum.PAID.getCode());
			}
		});
		if (!orderItemService.updateBatchById(orderItemEntityList)) {
			throw new ArynBusinessException("订单商品支付状态更新失败，请重试");
		}

		// 商城配送：支付后自动创建配送任务
		if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())) {
			deliveryTaskService.createTaskOnPay(orderInfo, orderItemEntityList);
		}

		// 通知销量增加、优惠券更改状态
		TransactionalMqUtils.sendAfterCommit(() -> orderPaySuccessNotifier.notify(orderInfo, orderItemEntityList));
	}

}
