package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 商城配送退款、退回和确认收货的服务端边界。 */
@Service
@RequiredArgsConstructor
public class DeliveryRefundBoundaryService {

	private final OrderDeliveryTaskMapper taskMapper;

	public void onRefundRequested(OrderInfo orderInfo) {
		if (!isMallDelivery(orderInfo)) {
			return;
		}
		OrderDeliveryTask task = requireTask(orderInfo);
		if (task.getPickedUpAt() == null || isReturnCompleted(task)
				|| DeliveryTaskStatusEnum.RETURN_PENDING.name().equals(task.getStatus())) {
			return;
		}
		if (taskMapper.markReturnPendingForRefund(orderInfo.getTenantId(), orderInfo.getId(), LocalDateTime.now()) == 1) {
			return;
		}
		OrderDeliveryTask current = requireTask(orderInfo);
		if (!DeliveryTaskStatusEnum.RETURN_PENDING.name().equals(current.getStatus()) && !isReturnCompleted(current)) {
			throw new ArynBusinessException("配送任务已变化，无法进入退回流程");
		}
	}

	public void requireRefundRelease(OrderInfo orderInfo) {
		if (!isMallDelivery(orderInfo)) {
			return;
		}
		OrderDeliveryTask task = requireTask(orderInfo);
		if (task.getPickedUpAt() != null && !isReturnCompleted(task)) {
			throw new ArynBusinessException("配送商品尚未确认退回，不能执行退款");
		}
	}

	public void requireDeliveredForReceipt(OrderInfo orderInfo) {
		if (!isMallDelivery(orderInfo)) {
			return;
		}
		OrderDeliveryTask task = requireTask(orderInfo);
		if (!DeliveryTaskStatusEnum.DELIVERED.name().equals(task.getStatus())) {
			throw new ArynBusinessException("商城配送任务尚未送达，不能确认收货");
		}
	}

	public void closeBeforePickupAfterFullRefund(OrderInfo orderInfo) {
		if (isMallDelivery(orderInfo)) {
			taskMapper.closeBeforePickupAfterFullRefund(orderInfo.getTenantId(), orderInfo.getId(), LocalDateTime.now());
		}
	}

	private OrderDeliveryTask requireTask(OrderInfo orderInfo) {
		OrderDeliveryTask task = taskMapper.selectByTenantAndOrderId(orderInfo.getTenantId(), orderInfo.getId());
		if (task == null) {
			throw new ArynBusinessException("商城配送任务不存在");
		}
		return task;
	}

	private boolean isReturnCompleted(OrderDeliveryTask task) {
		return DeliveryTaskStatusEnum.CLOSED.name().equals(task.getStatus()) && task.getReturnedAt() != null;
	}

	private boolean isMallDelivery(OrderInfo orderInfo) {
		return orderInfo != null && MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay());
	}

}
