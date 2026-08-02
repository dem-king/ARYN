
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryTripMapper;
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
 * 出车单
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
public class DeliveryTripServiceImpl extends ServiceImpl<DeliveryTripMapper, DeliveryTrip>
		implements IDeliveryTripService {

	private final IDeliveryTaskService deliveryTaskService;

	private final IDeliveryTaskItemService deliveryTaskItemService;

	public DeliveryTripServiceImpl(@Lazy IDeliveryTaskService deliveryTaskService,
			IDeliveryTaskItemService deliveryTaskItemService) {
		this.deliveryTaskService = deliveryTaskService;
		this.deliveryTaskItemService = deliveryTaskItemService;
	}

	@Override
	public DeliveryTrip getTripDetail(String id) {
		DeliveryTrip trip = getById(id);
		if (trip == null) {
			return null;
		}
		List<DeliveryTask> taskList = deliveryTaskService
			.list(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTripId, id).orderByAsc(DeliveryTask::getSortNo));
		if (CollUtil.isNotEmpty(taskList)) {
			taskList.forEach(task -> task.setItemList(deliveryTaskItemService.listByTaskId(task.getId())));
		}
		trip.setTaskList(taskList);
		return trip;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean startLoading(String tripId, String staffId) {
		DeliveryTrip trip = getAndCheckActiveTrip(tripId, staffId);
		if (!DeliveryTripStatusEnum.WAITING_LOAD.getCode().equals(trip.getStatus())) {
			throw new ArynBusinessException("当前出车单状态不允许开始配货");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTrip>lambdaUpdate()
			.eq(DeliveryTrip::getId, tripId)
			.eq(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode())
			.set(DeliveryTrip::getStatus, DeliveryTripStatusEnum.LOADING.getCode())
			.set(DeliveryTrip::getStartLoadTime, now));
		if (updated == 0) {
			throw new ArynBusinessException("出车单状态已变化，无法开始配货");
		}
		// 关联的所有任务状态变为配货中
		deliveryTaskService.update(Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getTripId, tripId)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_PICK.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.PICKING.getCode())
			.set(DeliveryTask::getPickUpTime, now));
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean depart(String tripId, String staffId) {
		DeliveryTrip trip = getAndCheckActiveTrip(tripId, staffId);
		if (!DeliveryTripStatusEnum.LOADING.getCode().equals(trip.getStatus())) {
			throw new ArynBusinessException("当前出车单状态不允许出发");
		}
		// 校验该 trip 下所有 task 的所有 item 都 picked=1
		if (!deliveryTaskItemService.allPicked(tripId)) {
			throw new ArynBusinessException("存在未确认取货的明细，无法出发");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTrip>lambdaUpdate()
			.eq(DeliveryTrip::getId, tripId)
			.eq(DeliveryTrip::getStatus, DeliveryTripStatusEnum.LOADING.getCode())
			.set(DeliveryTrip::getStatus, DeliveryTripStatusEnum.DELIVERING.getCode())
			.set(DeliveryTrip::getDepartTime, now));
		if (updated == 0) {
			throw new ArynBusinessException("出车单状态已变化，无法出发");
		}
		// 所有 task 状态变为待送达
		deliveryTaskService.update(Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getTripId, tripId)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.PICKING.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())
			.set(DeliveryTask::getDepartTime, now));
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean adjustSort(String tripId, String staffId, List<String> taskIds) {
		if (CollUtil.isEmpty(taskIds)) {
			throw new ArynBusinessException("任务ID列表不能为空");
		}
		getAndCheckActiveTrip(tripId, staffId);
		for (int i = 0; i < taskIds.size(); i++) {
			deliveryTaskService.update(Wrappers.<DeliveryTask>lambdaUpdate()
				.eq(DeliveryTask::getId, taskIds.get(i))
				.eq(DeliveryTask::getTripId, tripId)
				.set(DeliveryTask::getSortNo, i + 1));
		}
		return Boolean.TRUE;
	}

	@Override
	public DeliveryTrip getActiveTrip(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return null;
		}
		return getOne(Wrappers.<DeliveryTrip>lambdaQuery()
			.eq(DeliveryTrip::getStaffId, staffId)
			.in(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode(),
					DeliveryTripStatusEnum.LOADING.getCode(), DeliveryTripStatusEnum.DELIVERING.getCode())
			.orderByDesc(DeliveryTrip::getCreateTime)
			.last("LIMIT 1"));
	}

	/**
	 * 校验出车单属于当前配送员且处于活跃状态
	 */
	private DeliveryTrip getAndCheckActiveTrip(String tripId, String staffId) {
		DeliveryTrip trip = getById(tripId);
		if (trip == null) {
			throw new ArynBusinessException("出车单不存在");
		}
		if (!trip.getStaffId().equals(staffId)) {
			throw new ArynBusinessException("无权操作该出车单");
		}
		return trip;
	}

}