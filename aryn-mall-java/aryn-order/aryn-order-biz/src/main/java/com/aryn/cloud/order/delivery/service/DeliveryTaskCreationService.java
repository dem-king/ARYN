package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskItem;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付成功后创建商城配送任务。
 */
@Service
@RequiredArgsConstructor
public class DeliveryTaskCreationService {

	private static final String TASK_NO_PREFIX = "MD";

	private final OrderDeliveryTaskMapper taskMapper;

	private final OrderDeliveryTaskItemMapper taskItemMapper;

	public OrderDeliveryTask createIfNeeded(OrderInfo order, List<OrderItemEntity> orderItems) {
		if (order == null || !MallOrderConstants.DELIVERY_WAY_3.equals(order.getDeliveryWay())) {
			return null;
		}
		validate(order, orderItems);

		LocalDateTime now = LocalDateTime.now();
		String taskId = IdWorker.getIdStr();
		OrderDeliveryTask task = new OrderDeliveryTask()
			.setId(taskId)
			.setTaskNo(TASK_NO_PREFIX + taskId)
			.setOrderId(order.getId())
			.setOrderNo(order.getOrderNo())
			.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT.name())
			.setAttemptNo(1)
			.setVersion(0)
			.setTenantId(order.getTenantId())
			.setCreateBy(order.getCreateBy())
			.setCreateTime(now)
			.setDelFlag(CommonConstants.NO);
		try {
			if (taskMapper.insert(task) != 1) {
				throw new ArynBusinessException("商城配送任务创建失败，请重试");
			}
		}
		catch (DuplicateKeyException exception) {
			OrderDeliveryTask existing = taskMapper.selectByTenantAndOrderId(order.getTenantId(), order.getId());
			if (existing == null) {
				throw exception;
			}
			return existing;
		}

		for (OrderItemEntity orderItem : orderItems) {
			OrderDeliveryTaskItem taskItem = new OrderDeliveryTaskItem()
				.setId(IdWorker.getIdStr())
				.setTaskId(taskId)
				.setOrderItemId(orderItem.getId())
				.setAttemptNo(1)
				.setChecked(CommonConstants.NO)
				.setTenantId(order.getTenantId())
				.setCreateBy(order.getCreateBy())
				.setCreateTime(now)
				.setDelFlag(CommonConstants.NO);
			if (taskItemMapper.insert(taskItem) != 1) {
				throw new ArynBusinessException("商城配送任务明细创建失败，请重试");
			}
		}
		return task;
	}

	private void validate(OrderInfo order, List<OrderItemEntity> orderItems) {
		if (!StringUtils.hasText(order.getId()) || !StringUtils.hasText(order.getOrderNo())
				|| !StringUtils.hasText(order.getTenantId())) {
			throw new ArynBusinessException("商城配送订单信息不完整");
		}
		if (CollectionUtils.isEmpty(orderItems)
				|| orderItems.stream().anyMatch(item -> item == null || !StringUtils.hasText(item.getId()))) {
			throw new ArynBusinessException("商城配送订单商品不能为空");
		}
	}

}
