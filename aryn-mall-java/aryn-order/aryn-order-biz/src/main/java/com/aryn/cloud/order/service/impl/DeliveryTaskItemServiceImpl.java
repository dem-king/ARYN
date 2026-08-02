
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryTaskItemMapper;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 取货明细
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
public class DeliveryTaskItemServiceImpl extends ServiceImpl<DeliveryTaskItemMapper, DeliveryTaskItem>
		implements IDeliveryTaskItemService {

	private final IDeliveryTaskService deliveryTaskService;

	private final IDeliveryTripService deliveryTripService;

	public DeliveryTaskItemServiceImpl(@Lazy IDeliveryTaskService deliveryTaskService,
			@Lazy IDeliveryTripService deliveryTripService) {
		this.deliveryTaskService = deliveryTaskService;
		this.deliveryTripService = deliveryTripService;
	}

	@Override
	public List<DeliveryTaskItem> listByTaskId(String taskId) {
		return list(Wrappers.<DeliveryTaskItem>lambdaQuery().eq(DeliveryTaskItem::getTaskId, taskId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean pick(String itemId, String staffId) {
		DeliveryTaskItem item = getById(itemId);
		if (item == null) {
			throw new ArynBusinessException("取货明细不存在");
		}
		// 校验 task 属于当前配送员的活跃 trip
		checkItemOwnership(item, staffId);
		if ("1".equals(item.getPicked())) {
			return Boolean.TRUE;
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTaskItem>lambdaUpdate()
			.eq(DeliveryTaskItem::getId, itemId)
			.eq(DeliveryTaskItem::getPicked, "0")
			.set(DeliveryTaskItem::getPicked, "1")
			.set(DeliveryTaskItem::getPickedTime, LocalDateTime.now()));
		if (updated == 0) {
			throw new ArynBusinessException("取货确认失败，请重试");
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean unpick(String itemId, String staffId) {
		DeliveryTaskItem item = getById(itemId);
		if (item == null) {
			throw new ArynBusinessException("取货明细不存在");
		}
		checkItemOwnership(item, staffId);
		if ("0".equals(item.getPicked())) {
			return Boolean.TRUE;
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTaskItem>lambdaUpdate()
			.eq(DeliveryTaskItem::getId, itemId)
			.eq(DeliveryTaskItem::getPicked, "1")
			.set(DeliveryTaskItem::getPicked, "0")
			.set(DeliveryTaskItem::getPickedTime, null));
		if (updated == 0) {
			throw new ArynBusinessException("取消取货失败，请重试");
		}
		return Boolean.TRUE;
	}

	@Override
	public boolean allPicked(String tripId) {
		if (StrUtil.isBlank(tripId)) {
			return Boolean.FALSE;
		}
		// 查询该 trip 下所有 task 的所有 item
		List<DeliveryTask> tasks = deliveryTaskService
			.list(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTripId, tripId));
		if (CollUtil.isEmpty(tasks)) {
			return Boolean.FALSE;
		}
		List<String> taskIds = tasks.stream().map(DeliveryTask::getId).toList();
		long unsignedCount = count(Wrappers.<DeliveryTaskItem>lambdaQuery()
			.in(DeliveryTaskItem::getTaskId, taskIds)
			.eq(DeliveryTaskItem::getPicked, "0"));
		return unsignedCount == 0;
	}

	/**
	 * 校验取货明细所属任务属于当前配送员的活跃出车单
	 */
	private void checkItemOwnership(DeliveryTaskItem item, String staffId) {
		DeliveryTask task = deliveryTaskService.getById(item.getTaskId());
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!task.getStaffId().equals(staffId)) {
			throw new ArynBusinessException("无权操作该取货明细");
		}
		if (StrUtil.isBlank(task.getTripId())) {
			throw new ArynBusinessException("任务尚未派单");
		}
		DeliveryTrip trip = deliveryTripService.getById(task.getTripId());
		if (trip == null) {
			throw new ArynBusinessException("出车单不存在");
		}
		// 仅配货中状态允许确认取货
		if (!DeliveryTripStatusEnum.LOADING.getCode().equals(trip.getStatus())) {
			throw new ArynBusinessException("出车单当前状态不允许确认取货");
		}
	}

}