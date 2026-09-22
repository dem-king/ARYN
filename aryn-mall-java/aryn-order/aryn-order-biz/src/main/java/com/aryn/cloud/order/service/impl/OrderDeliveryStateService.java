
package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.service.IOrderDeliveryStateService;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 商城配送/内部配送的订单状态联动。
 *
 * <p>历史缺陷：只有第三方快递的 {@code deliverOrder} 会把订单推进到「待收货」，
 * 配送任务状态机（派单→取货→出发→送达）全程不碰订单表，
 * 导致 delivery_way=3（商城配送）与 delivery_way=4（公司港口/船舶内部配送）
 * 的订单永远停留在「待发货」，客户无法确认收货、订单无法完成。
 *
 * <p>本服务把配送任务的关键节点回写到订单：
 * 确认取货出发 → 订单「待收货」、订单项「已发货」并记录发货时间；
 * 客户签收 → 订单「已完成」（由现有 receiveOrder 承担）。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDeliveryStateService implements IOrderDeliveryStateService {

	private final OrderInfoMapper orderInfoMapper;

	private final OrderItemMapper orderItemMapper;

	private final OrderWxDeliveryService orderWxDeliveryService;

	/**
	 * 该配送方式是否由配送任务驱动订单状态（区别于第三方快递的发货单驱动）。
	 */
	public static boolean isTaskDrivenDeliveryWay(String deliveryWay) {
		return MallOrderConstants.DELIVERY_WAY_3.equals(deliveryWay)
				|| MallOrderConstants.DELIVERY_WAY_4.equals(deliveryWay);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean markShippedOnPickUp(DeliveryTask task) {
		if (task == null || task.getOrderId() == null) {
			return Boolean.FALSE;
		}
		OrderInfo orderInfo = orderInfoMapper.selectById(task.getOrderId());
		if (orderInfo == null) {
			log.warn("配送任务[{}]关联订单[{}]不存在，跳过状态联动", task.getId(), task.getOrderId());
			return Boolean.FALSE;
		}
		if (!isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay())) {
			// 第三方快递仍由发货单驱动，避免双写冲突
			return Boolean.FALSE;
		}
		if (OrderStatusEnum.COMPLETED.getCode().equals(orderInfo.getStatus())
				|| OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())) {
			// 订单已终结，不再回退
			return Boolean.FALSE;
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = orderInfoMapper.update(null, Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode())
			.set(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())
			.set(OrderInfo::getDeliverTime, now));
		if (updated == 0) {
			// 幂等：重复出发不应报错，状态已是待收货即视为成功
			OrderInfo current = orderInfoMapper.selectById(orderInfo.getId());
			if (current != null && OrderStatusEnum.WAITING_FOR_RECEIPT.getCode().equals(current.getStatus())) {
				log.info("订单[{}]已是待收货，跳过重复联动", orderInfo.getId());
				return Boolean.TRUE;
			}
			log.warn("订单[{}]状态联动失败，当前状态[{}]", orderInfo.getId(), orderInfo.getStatus());
			return Boolean.FALSE;
		}
		orderItemMapper.update(null, Wrappers.<OrderItemEntity>lambdaUpdate()
			.eq(OrderItemEntity::getOrderId, orderInfo.getId())
			.eq(OrderItemEntity::getStatus, OrderItemStatusEnum.PAID.getCode())
			.set(OrderItemEntity::getStatus, OrderItemStatusEnum.SHIPPED.getCode()));
		log.info("订单[{}]配送方式[{}]已出发，订单转为待收货", orderInfo.getId(), orderInfo.getDeliveryWay());

		// 微信小程序发货信息上报：公司的司机配送按「同城配送」上报，避免影响支付能力。
		// 仅在事务真正提交后调用外部接口，重复出发不会重复上报（本方法上面已按状态幂等返回）。
		OrderInfo shippedOrder = new OrderInfo();
		shippedOrder.setId(orderInfo.getId());
		shippedOrder.setAppId(orderInfo.getAppId());
		shippedOrder.setTradeType(orderInfo.getTradeType());
		shippedOrder.setPaymentType(orderInfo.getPaymentType());
		shippedOrder.setOpenId(orderInfo.getOpenId());
		shippedOrder.setTransactionId(orderInfo.getTransactionId());
		shippedOrder.setDeliveryWay(orderInfo.getDeliveryWay());
		shippedOrder.setStatus(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode());
		java.util.List<OrderItemEntity> orderItems = orderItemMapper.selectList(
				Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, orderInfo.getId()));
		if (!orderItems.isEmpty()) {
			TransactionalMqUtils.sendAfterCommit(() -> {
				try {
					orderWxDeliveryService.uploadDeliveryInfoOnTaskShipped(shippedOrder, orderItems);
				}
				catch (Exception ex) {
					// 上报失败不能影响订单状态推进，由运营按微信后台补报
					log.warn("订单[{}]微信发货信息上报失败: {}", orderInfo.getId(), ex.getMessage());
				}
			});
		}
		return Boolean.TRUE;
	}

	@Override
	public boolean isTaskDrivenOrder(String orderId) {
		if (orderId == null) {
			return Boolean.FALSE;
		}
		OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
		return orderInfo != null && isTaskDrivenDeliveryWay(orderInfo.getDeliveryWay());
	}
}
