
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryPickupSummaryVO;
import com.aryn.cloud.order.api.vo.DeliveryTripBriefVO;
import com.aryn.cloud.order.api.vo.DeliveryTripTaskBriefVO;
import com.aryn.cloud.order.mapper.DeliveryTripMapper;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import com.aryn.cloud.order.service.IOrderDeliveryStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

	private final IOrderDeliveryStateService orderDeliveryStateService;

	private final IDeliveryWarehouseConfigService deliveryWarehouseConfigService;

	private final IDeliveryStaffService deliveryStaffService;

	public DeliveryTripServiceImpl(@Lazy IDeliveryTaskService deliveryTaskService,
			IDeliveryTaskItemService deliveryTaskItemService,
			IOrderDeliveryStateService orderDeliveryStateService,
			IDeliveryWarehouseConfigService deliveryWarehouseConfigService,
			IDeliveryStaffService deliveryStaffService) {
		this.deliveryTaskService = deliveryTaskService;
		this.deliveryTaskItemService = deliveryTaskItemService;
		this.orderDeliveryStateService = orderDeliveryStateService;
		this.deliveryWarehouseConfigService = deliveryWarehouseConfigService;
		this.deliveryStaffService = deliveryStaffService;
	}

	@Override
	public DeliveryTrip getTripDetail(String id) {
		DeliveryTrip trip = getById(id);
		if (trip == null) {
			return null;
		}
		// 出车单自身与任务行都要回填配送员姓名：详情页顶部展示出车单的，任务表格展示任务行的
		fillStaffName(List.of(trip));
		List<DeliveryTask> taskList = deliveryTaskService
			.list(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTripId, id).orderByAsc(DeliveryTask::getSortNo));
		if (CollUtil.isNotEmpty(taskList)) {
			// 全部任务的明细合并后一次性回填分类名，避免逐任务发起商品域调用
			List<DeliveryTaskItem> allItems = new ArrayList<>();
			taskList.forEach(task -> {
				List<DeliveryTaskItem> items = deliveryTaskItemService.listByTaskId(task.getId());
				task.setItemList(items);
				allItems.addAll(items);
			});
			deliveryTaskItemService.fillCategoryName(allItems);
			deliveryTaskService.fillStaffName(taskList);
		}
		trip.setTaskList(taskList);
		fillDerivedFields(trip);
		return trip;
	}

	@Override
	public IPage<DeliveryTrip> pageWithStaffName(Page<DeliveryTrip> page, Wrapper<DeliveryTrip> wrapper) {
		IPage<DeliveryTrip> result = page(page, wrapper);
		fillStaffName(result.getRecords());
		return result;
	}

	private void fillStaffName(List<DeliveryTrip> trips) {
		if (CollUtil.isEmpty(trips)) {
			return;
		}
		Map<String, String> names = deliveryStaffService.mapStaffNames(trips.stream()
			.map(DeliveryTrip::getStaffId)
			.toList());
		if (names.isEmpty()) {
			return;
		}
		trips.forEach(trip -> trip.setStaffName(names.get(trip.getStaffId())));
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
		boolean alreadyDeparted = DeliveryTripStatusEnum.DELIVERING.getCode().equals(trip.getStatus());
		LocalDateTime now = LocalDateTime.now();
		if (!alreadyDeparted) {
			if (!DeliveryTripStatusEnum.LOADING.getCode().equals(trip.getStatus())) {
				throw new ArynBusinessException("当前出车单状态不允许出发");
			}
			// 首次出发：整趟必须配齐（已出发后新增的单由下面的增量分支单独校验）
			if (!deliveryTaskItemService.allPicked(tripId)) {
				throw new ArynBusinessException("存在未确认取货的明细，无法出发");
			}
			int updated = baseMapper.update(null, Wrappers.<DeliveryTrip>lambdaUpdate()
				.eq(DeliveryTrip::getId, tripId)
				.eq(DeliveryTrip::getStatus, DeliveryTripStatusEnum.LOADING.getCode())
				.set(DeliveryTrip::getStatus, DeliveryTripStatusEnum.DELIVERING.getCode())
				.set(DeliveryTrip::getDepartTime, now));
			if (updated == 0) {
				throw new ArynBusinessException("出车单状态已变化，无法出发");
			}
		}
		// 把「配货中」的任务推到「待送达」。已出发的趟次重复点出发时，
		// 这里只会命中后来新并入、还没送出去的单 —— 让补加的单也能上路。
		//
		// 单张的取货校验只在重复出发分支做：首次出发已在上面按整趟校验过。
		List<DeliveryTask> departedTasks = deliveryTaskService.list(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.PICKING.getCode()));
		if (alreadyDeparted && CollUtil.isNotEmpty(departedTasks)) {
			for (DeliveryTask task : departedTasks) {
				if (!deliveryTaskItemService.allPickedByTask(task.getId())) {
					throw new ArynBusinessException("订单[" + task.getOrderNo() + "]还有未确认取货的明细，无法出发");
				}
			}
		}
		deliveryTaskService.update(Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getTripId, tripId)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.PICKING.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())
			.set(DeliveryTask::getDepartTime, now));
		// 订单状态联动：商城配送/内部配送订单在商品离仓后进入「待收货」
		// 第三方快递订单不受影响，仍由发货单驱动
		for (DeliveryTask task : departedTasks) {
			try {
				orderDeliveryStateService.markShippedOnPickUp(task);
			}
			catch (Exception ex) {
				log.error("订单状态联动失败，任务[{}]，订单[{}]", task.getId(), task.getOrderId(), ex);
				throw ex;
			}
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean adjustSort(String tripId, String staffId, List<String> taskIds) {
		if (CollUtil.isEmpty(taskIds)) {
			throw new ArynBusinessException("任务ID列表不能为空");
		}
		getAndCheckActiveTrip(tripId, staffId);
		// 必须交出本趟全部任务的一个排列：司机端列表可能基于旧快照，
		// 只提交部分任务会把未提交的留在原序号上，路线静默错乱
		List<String> tripTaskIds = deliveryTaskService.list(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId))
			.stream()
			.map(DeliveryTask::getId)
			.toList();
		if (taskIds.size() != tripTaskIds.size() || !taskIds.containsAll(tripTaskIds)) {
			throw new ArynBusinessException("送货顺序与当前趟次不一致，请刷新后重试");
		}
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
		DeliveryTrip trip = getOne(Wrappers.<DeliveryTrip>lambdaQuery()
			.eq(DeliveryTrip::getStaffId, staffId)
			.in(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode(),
					DeliveryTripStatusEnum.LOADING.getCode(), DeliveryTripStatusEnum.DELIVERING.getCode())
			.orderByDesc(DeliveryTrip::getCreateTime)
			.last("LIMIT 1"));
		if (trip == null) {
			return null;
		}
		// 复用详情装配，补充任务明细与派生统计，供配送员首页进度展示
		return getTripDetail(trip.getId());
	}

	/**
	 * 任务结清状态：司机侧已无待办。
	 *
	 * <p>已送达即司机履约终点（送达是既成事实，不允许回退）；已签收是客户确认后的
	 * 订单域事实；已取消（异常关闭、退回确认）由管理员收口。这些状态下出车单都不应
	 * 继续挂在「配送中」，否则司机的「当前出车单」会永久停留在配送中。
	 *
	 * <p>待退回不计入结清：退款退回是司机的实际差事，等管理员确认商品回仓（转已取消）
	 * 后出车单才收车。
	 */
	private static final List<String> SETTLED_TASK_STATUSES = List.of(
			DeliveryTaskStatusEnum.ARRIVED.getCode(), DeliveryTaskStatusEnum.SIGNED.getCode(),
			DeliveryTaskStatusEnum.CANCELED.getCode());

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean completeIfAllTasksSettled(String tripId) {
		if (StrUtil.isBlank(tripId)) {
			return Boolean.FALSE;
		}
		long taskCount = deliveryTaskService.count(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId));
		if (taskCount == 0) {
			return Boolean.FALSE;
		}
		// 仍有待送达/异常等未结清任务时保持进行中，异常任务等管理员改派或关闭
		long unsettledCount = deliveryTaskService.count(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId)
			.notIn(DeliveryTask::getStatus, SETTLED_TASK_STATUSES));
		if (unsettledCount > 0) {
			return Boolean.FALSE;
		}
		// 条件更新：并发（多单同时送达）时只有一次会把在途出车单置为已完成
		return baseMapper.update(null, Wrappers.<DeliveryTrip>lambdaUpdate()
			.eq(DeliveryTrip::getId, tripId)
			.in(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode(),
					DeliveryTripStatusEnum.LOADING.getCode(), DeliveryTripStatusEnum.DELIVERING.getCode())
			.set(DeliveryTrip::getStatus, DeliveryTripStatusEnum.COMPLETED.getCode())
			.set(DeliveryTrip::getCompleteTime, LocalDateTime.now())) > 0;
	}

	@Override
	public List<DeliveryTripBriefVO> listActiveTripBriefs(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return List.of();
		}
		List<DeliveryTrip> trips = list(Wrappers.<DeliveryTrip>lambdaQuery()
			.eq(DeliveryTrip::getStaffId, staffId)
			.in(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode(),
					DeliveryTripStatusEnum.LOADING.getCode(), DeliveryTripStatusEnum.DELIVERING.getCode())
			.orderByAsc(DeliveryTrip::getCreateTime));
		if (CollUtil.isEmpty(trips)) {
			return List.of();
		}
		List<String> tripIds = trips.stream().map(DeliveryTrip::getId).toList();
		// 一次取出所有在途趟次的任务，避免逐趟查询
		List<DeliveryTask> allTasks = deliveryTaskService.list(Wrappers.<DeliveryTask>lambdaQuery()
			.in(DeliveryTask::getTripId, tripIds)
			.orderByAsc(DeliveryTask::getSortNo));
		Map<String, List<DeliveryTask>> tasksByTripId = allTasks.stream()
			.collect(Collectors.groupingBy(DeliveryTask::getTripId, LinkedHashMap::new, Collectors.toList()));
		// 明细只用于统计件数，不返回给工作台（首屏要装下整趟车）
		Map<String, List<DeliveryTaskItem>> itemsByTaskId = CollUtil.isEmpty(allTasks)
				? Map.of()
				: deliveryTaskItemService.list(Wrappers.<DeliveryTaskItem>lambdaQuery()
					.in(DeliveryTaskItem::getTaskId, allTasks.stream().map(DeliveryTask::getId).toList()))
					.stream()
					.collect(Collectors.groupingBy(DeliveryTaskItem::getTaskId));

		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		String warehouseName = warehouseConfig == null ? null : warehouseConfig.getWarehouseName();
		List<DeliveryTripBriefVO> briefs = new ArrayList<>(trips.size());
		for (DeliveryTrip trip : trips) {
			List<DeliveryTask> tasks = tasksByTripId.getOrDefault(trip.getId(), List.of());
			DeliveryTripBriefVO brief = new DeliveryTripBriefVO();
			brief.setId(trip.getId());
			brief.setTripNo(trip.getTripNo());
			brief.setStatus(trip.getStatus());
			brief.setWarehouseName(warehouseName);
			brief.setWarehouseAddress(trip.getWarehouseAddress());
			brief.setDepartTime(trip.getDepartTime());
			brief.setTaskCount(tasks.size());
			brief.setArrivedTaskCount((int) tasks.stream().filter(task -> task.getArriveTime() != null).count());
			int totalItemCount = 0;
			int pickedItemCount = 0;
			for (DeliveryTask task : tasks) {
				for (DeliveryTaskItem item : itemsByTaskId.getOrDefault(task.getId(), List.of())) {
					totalItemCount++;
					if ("1".equals(item.getPicked())) {
						pickedItemCount++;
					}
				}
			}
			brief.setTotalItemCount(totalItemCount);
			brief.setPickedItemCount(pickedItemCount);
			brief.setTaskList(tasks.stream().map(this::toTaskBrief).toList());
			briefs.add(brief);
		}
		return briefs;
	}

	/**
	 * 任务行转工作台摘要行：只带司机在列表上做判断需要的字段。
	 */
	private DeliveryTripTaskBriefVO toTaskBrief(DeliveryTask task) {
		DeliveryTripTaskBriefVO brief = new DeliveryTripTaskBriefVO();
		brief.setId(task.getId());
		brief.setTaskNo(task.getTaskNo());
		brief.setOrderNo(task.getOrderNo());
		brief.setSortNo(task.getSortNo());
		brief.setStatus(task.getStatus());
		brief.setRecipientName(task.getRecipientName());
		brief.setRecipientPhone(task.getRecipientPhone());
		brief.setRecipientAddress(task.getRecipientAddress());
		brief.setVesselName(task.getVesselName());
		brief.setPortName(task.getPortName());
		brief.setBerth(task.getBerth());
		brief.setDeliveryWindowStart(task.getDeliveryWindowStart());
		brief.setDeliveryWindowEnd(task.getDeliveryWindowEnd());
		brief.setArriveTime(task.getArriveTime());
		return brief;
	}

	/**
	 * 填充响应派生字段：件数统计、取货清单汇总与仓库名称。均不入库。
	 */
	private void fillDerivedFields(DeliveryTrip trip) {
		int totalItemCount = 0;
		int pickedItemCount = 0;
		int arrivedTaskCount = 0;
		List<DeliveryTask> taskList = trip.getTaskList();
		if (CollUtil.isNotEmpty(taskList)) {
			for (DeliveryTask task : taskList) {
				List<DeliveryTaskItem> items = task.getItemList();
				if (CollUtil.isNotEmpty(items)) {
					totalItemCount += items.size();
					pickedItemCount += (int) items.stream()
						.filter(item -> "1".equals(item.getPicked()))
						.count();
				}
				if (task.getArriveTime() != null) {
					arrivedTaskCount++;
				}
			}
		}
		trip.setTotalItemCount(totalItemCount);
		trip.setPickedItemCount(pickedItemCount);
		trip.setArrivedTaskCount(arrivedTaskCount);
		trip.setPickupSummary(buildPickupSummary(taskList));
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		if (warehouseConfig != null) {
			trip.setWarehouseName(warehouseConfig.getWarehouseName());
		}
		// 司机端据此决定是否显示「未派送订单」入口：关掉后该入口拉不到任何单
		trip.setSelfPullUnassignedAllowed(deliveryTaskService.isDriverSelfPullUnassignedAllowed());
	}

	/**
	 * 按「商品 + 规格」汇总出车单下所有任务的取货明细，规格为空按空串参与聚合。
	 */
	private List<DeliveryPickupSummaryVO> buildPickupSummary(List<DeliveryTask> taskList) {
		if (CollUtil.isEmpty(taskList)) {
			return List.of();
		}
		Map<String, DeliveryPickupSummaryVO> merged = new LinkedHashMap<>();
		Map<String, Integer> quantities = new LinkedHashMap<>();
		for (DeliveryTask task : taskList) {
			for (DeliveryTaskItem item : CollUtil.emptyIfNull(task.getItemList())) {
				String key = StrUtil.nullToEmpty(item.getSpuName()) + '\u0000' + StrUtil.nullToEmpty(item.getSkuName());
				DeliveryPickupSummaryVO row = merged.get(key);
				if (row == null) {
					row = new DeliveryPickupSummaryVO();
					row.setSpuName(item.getSpuName());
					row.setSpecsInfo(item.getSkuName());
					row.setPicUrl(item.getImage());
					row.setQuantity(0);
					merged.put(key, row);
					quantities.put(key, 0);
				}
				quantities.put(key, quantities.get(key) + (item.getQuantity() == null ? 0 : item.getQuantity()));
			}
		}
		List<DeliveryPickupSummaryVO> summary = new ArrayList<>(merged.values());
		summary.forEach(row -> {
			String key = StrUtil.nullToEmpty(row.getSpuName()) + '\u0000' + StrUtil.nullToEmpty(row.getSpecsInfo());
			row.setQuantity(quantities.get(key));
		});
		return summary;
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