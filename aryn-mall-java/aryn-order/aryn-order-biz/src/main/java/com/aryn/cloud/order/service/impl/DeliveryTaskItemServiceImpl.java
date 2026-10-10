
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.mapper.DeliveryTaskItemMapper;import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

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

	private final IOrderItemService orderItemService;

	/** 商品域：按 spuId 批量回填类目名（明细快照表不存分类，分类≈供应商批次） */
	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	public DeliveryTaskItemServiceImpl(@Lazy IDeliveryTaskService deliveryTaskService,
			@Lazy IDeliveryTripService deliveryTripService, IOrderItemService orderItemService) {
		this.deliveryTaskService = deliveryTaskService;
		this.deliveryTripService = deliveryTripService;
		this.orderItemService = orderItemService;
	}

	@Override
	public List<DeliveryTaskItem> listByTaskId(String taskId) {
		return list(Wrappers.<DeliveryTaskItem>lambdaQuery().eq(DeliveryTaskItem::getTaskId, taskId));
	}

	@Override
	public void fillCategoryName(List<DeliveryTaskItem> items) {
		if (CollUtil.isEmpty(items)) {
			return;
		}
		List<String> orderItemIds = items.stream()
			.map(DeliveryTaskItem::getOrderItemId)
			.filter(StrUtil::isNotBlank)
			.distinct()
			.toList();
		if (orderItemIds.isEmpty()) {
			return;
		}
		Map<String, String> spuIdByOrderItemId = orderItemService.listByIds(orderItemIds)
			.stream()
			.filter(orderItem -> StrUtil.isNotBlank(orderItem.getSpuId()))
			.collect(Collectors.toMap(OrderItemEntity::getId, OrderItemEntity::getSpuId, (first, second) -> first));
		if (spuIdByOrderItemId.isEmpty()) {
			return;
		}
		Map<String, GoodsSpu> spuById = remoteGoodsSpuService
			.getSpuByIds(spuIdByOrderItemId.values().stream().distinct().toList())
			.stream()
			.collect(Collectors.toMap(GoodsSpu::getId, Function.identity(), (first, second) -> first));
		if (spuById.isEmpty()) {
			return;
		}
		items.forEach(item -> {
			String spuId = spuIdByOrderItemId.get(item.getOrderItemId());
			if (spuId == null) {
				return;
			}
			GoodsSpu spu = spuById.get(spuId);
			if (Objects.nonNull(spu) && StrUtil.isNotBlank(spu.getCategoryName())) {
				item.setCategoryName(spu.getCategoryName());
			}
		});
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
	@Transactional(rollbackFor = Exception.class)
	public int batchPick(String tripId, List<String> itemIds, boolean picked, String staffId) {
		if (CollUtil.isEmpty(itemIds)) {
			return 0;
		}
		// 校验口径与逐项 pick 一致：只能操作自己的在途出车单下的明细
		DeliveryTrip trip = deliveryTripService.getById(tripId);
		if (trip == null) {
			throw new ArynBusinessException("出车单不存在");
		}
		if (!Objects.equals(trip.getStaffId(), staffId)) {
			throw new ArynBusinessException("无权操作该出车单");
		}
		boolean delivering = DeliveryTripStatusEnum.DELIVERING.getCode().equals(trip.getStatus());
		if (!DeliveryTripStatusEnum.LOADING.getCode().equals(trip.getStatus()) && !delivering) {
			throw new ArynBusinessException("出车单当前状态不允许确认取货");
		}
		List<DeliveryTask> tasks = deliveryTaskService
			.list(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTripId, tripId));
		List<String> taskIds = tasks.stream().map(DeliveryTask::getId).toList();
		if (taskIds.isEmpty()) {
			throw new ArynBusinessException("出车单下没有配送任务");
		}
		// 明细必须全部落在这趟车里：跨趟提交要么是前端串了数据，要么有人在拼请求
		List<DeliveryTaskItem> items = list(Wrappers.<DeliveryTaskItem>lambdaQuery()
			.in(DeliveryTaskItem::getId, itemIds)
			.in(DeliveryTaskItem::getTaskId, taskIds));
		if (items.size() != itemIds.stream().distinct().count()) {
			throw new ArynBusinessException("存在不属于该出车单的取货明细");
		}
		// 已出发的趟次只能配新并入的货：老任务早该在首次出发前配齐，
		// 允许改它们等于允许出发后偷偷改装车事实。
		if (delivering && !allBelongToPickingTasks(items)) {
			throw new ArynBusinessException("只能确认本趟新加的货，已出发的货不能改取货状态");
		}
		String fromPicked = picked ? "0" : "1";
		String toPicked = picked ? "1" : "0";
		return baseMapper.update(null, Wrappers.<DeliveryTaskItem>lambdaUpdate()
			.in(DeliveryTaskItem::getId, itemIds)
			.eq(DeliveryTaskItem::getPicked, fromPicked)
			.set(DeliveryTaskItem::getPicked, toPicked)
			.set(DeliveryTaskItem::getPickedTime, picked ? LocalDateTime.now() : null));
	}

	/**
	 * 这批明细是否全部挂在「配货中」的任务下（即本趟新并入、尚未出发的货）。
	 */
	private boolean allBelongToPickingTasks(List<DeliveryTaskItem> items) {
		List<String> taskIds = items.stream()
			.map(DeliveryTaskItem::getTaskId)
			.filter(StrUtil::isNotBlank)
			.distinct()
			.toList();
		if (taskIds.isEmpty()) {
			return false;
		}
		long pickingCount = deliveryTaskService.count(Wrappers.<DeliveryTask>lambdaQuery()
			.in(DeliveryTask::getId, taskIds)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.PICKING.getCode()));
		return pickingCount == taskIds.size();
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

	@Override
	public boolean allPickedByTask(String taskId) {
		if (StrUtil.isBlank(taskId)) {
			return Boolean.FALSE;
		}
		long unsignedCount = count(Wrappers.<DeliveryTaskItem>lambdaQuery()
			.eq(DeliveryTaskItem::getTaskId, taskId)
			.eq(DeliveryTaskItem::getPicked, "0"));
		return unsignedCount == 0;
	}

	/**
	 * 校验取货明细所属任务属于当前配送员的在途出车单。
	 *
	 * <p>「配货中」整趟可配；「配送中」只放行新并入的货（任务仍是配货中）——
	 * 车开出去以后司机回车取货、顺路捎带都是常态，不允许配就没法把这单送出去；
	 * 但已出发的老货不能再改取货状态。
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
		if (DeliveryTripStatusEnum.LOADING.getCode().equals(trip.getStatus())) {
			return;
		}
		if (DeliveryTripStatusEnum.DELIVERING.getCode().equals(trip.getStatus())) {
			if (DeliveryTaskStatusEnum.PICKING.getCode().equals(task.getStatus())) {
				return;
			}
			throw new ArynBusinessException("只能确认本趟新加的货，已出发的货不能改取货状态");
		}
		throw new ArynBusinessException("出车单当前状态不允许确认取货");
	}

}