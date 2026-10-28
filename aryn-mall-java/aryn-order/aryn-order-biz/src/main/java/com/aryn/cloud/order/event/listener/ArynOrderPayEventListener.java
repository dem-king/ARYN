
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
import org.apache.dubbo.config.annotation.DubboReference;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
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

	private final IOrderRefundService orderRefundService;

	private final OrderPaySuccessNotifier orderPaySuccessNotifier;

	private final IDeliveryTaskService deliveryTaskService;

	@DubboReference
	private final com.aryn.cloud.promotion.api.remote.RemotePromotionEngine promotionEngine;

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
		if (OrderStatusEnum.CANCELED.getCode().equals(orderInfo.getStatus())) {
			compensatePaidCanceledOrder(orderInfo);
			return;
		}

		// 商城配送方式：支付后即等待配送员派单，订单状态仍为待发货
		String targetStatus;
		if (MallOrderConstants.DELIVERY_WAY_2.equals(orderInfo.getDeliveryWay())) {
			targetStatus = OrderStatusEnum.WAITING_FOR_RECEIPT.getCode();
		}
		else {
			// 普通快递、商城配送与内部配送均进入待发货（仓库配货）
			targetStatus = OrderStatusEnum.WAITING_FOR_DELIVERY.getCode();
		}
		boolean updated = orderInfoService.update(Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
			.set(OrderInfo::getStatus, targetStatus)
			.set(OrderInfo::getPayStatus, CommonConstants.YES)
			.set(OrderInfo::getPaymentTime, orderInfo.getPaymentTime())
			.set(OrderInfo::getPaymentType, orderInfo.getPaymentType())
			.set(OrderInfo::getTransactionId, orderInfo.getTransactionId()));
		if (!updated) {
			OrderInfo current = orderInfoService.getById(orderInfo.getId());
			if (current != null && CommonConstants.YES.equals(current.getPayStatus())) {
				// 并发重复回调：另一事务已完成支付处理，幂等退出
				log.info("订单[{}]支付回调重复投递，已由并发事务处理，幂等退出", orderInfo.getId());
				return;
			}
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

		// 商城配送与内部配送：支付后自动创建配送任务（服务内部按唯一键幂等）
		if (MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
				|| MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay())) {
			deliveryTaskService.createTaskOnPay(orderInfo, orderItemEntityList);
		}

		// 营销优惠确认（锁定→确认；失败仅告警，不阻断支付主流程）
		try {
			promotionEngine.confirm(orderInfo.getTenantId(), orderInfo.getId());
		}
		catch (Exception ex) {
			log.warn("订单[{}]营销优惠确认失败", orderInfo.getId(), ex);
		}

		// 通知销量增加、优惠券更改状态
		TransactionalMqUtils.sendAfterCommit(() -> orderPaySuccessNotifier.notify(orderInfo, orderItemEntityList));
	}

	/**
	 * 订单已取消但渠道确认到账：先记录支付事实，再按明细原路退款。
	 * 取消链路已释放库存/券/积分，这里只补资金退款，避免二次回滚业务资源。
	 */
	private void compensatePaidCanceledOrder(OrderInfo orderInfo) {
		boolean updated = orderInfoService.update(Wrappers.<OrderInfo>lambdaUpdate()
			.eq(OrderInfo::getId, orderInfo.getId())
			.eq(OrderInfo::getStatus, OrderStatusEnum.CANCELED.getCode())
			.eq(OrderInfo::getPayStatus, CommonConstants.NO)
			.set(OrderInfo::getPayStatus, CommonConstants.YES)
			.set(OrderInfo::getPaymentTime, orderInfo.getPaymentTime())
			.set(OrderInfo::getPaymentType, orderInfo.getPaymentType())
			.set(OrderInfo::getTransactionId, orderInfo.getTransactionId()));
		if (!updated) {
			OrderInfo current = orderInfoService.getById(orderInfo.getId());
			if (current == null || !CommonConstants.YES.equals(current.getPayStatus())
					|| !OrderStatusEnum.CANCELED.getCode().equals(current.getStatus())) {
				throw new ArynBusinessException("订单取消后到账状态更新失败，请重试");
			}
			orderInfo.setPaymentTime(current.getPaymentTime() != null ? current.getPaymentTime()
					: orderInfo.getPaymentTime());
			orderInfo.setPaymentType(current.getPaymentType() != null ? current.getPaymentType()
					: orderInfo.getPaymentType());
			orderInfo.setTransactionId(current.getTransactionId() != null ? current.getTransactionId()
					: orderInfo.getTransactionId());
		}
		orderInfo.setPayStatus(CommonConstants.YES);
		boolean refunded = orderRefundService.refundPaidCanceledOrder(orderInfo, "订单取消后到账自动退款");
		log.warn("订单[{}]取消后到账，已记录支付并{}", orderInfo.getId(),
				refunded ? "发起自动退款" : "未产生新退款（可能已退款或需人工核对）");
	}

}
