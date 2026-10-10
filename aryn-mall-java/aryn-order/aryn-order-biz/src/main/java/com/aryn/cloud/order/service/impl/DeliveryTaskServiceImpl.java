
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.DeliveryAssignDTO;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.api.enums.DeliveryStaffStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryCandidateItemVO;
import com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO;
import com.aryn.cloud.order.api.vo.DeliveryProgressNode;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.*;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.upms.api.remote.RemoteMaterialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配送任务
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTaskServiceImpl extends ServiceImpl<DeliveryTaskMapper, DeliveryTask>
		implements IDeliveryTaskService {

	private final IDeliveryTripService deliveryTripService;

	private final IDeliveryTaskItemService deliveryTaskItemService;

	private final IDeliveryStaffService deliveryStaffService;

	private final IDeliveryWarehouseConfigService deliveryWarehouseConfigService;

	private final IDeliveryTaskLogService deliveryTaskLogService;

	private final IDeliveryEvidenceService deliveryEvidenceService;

	@DubboReference
	private RemoteMaterialService remoteMaterialService;

	private final IOrderItemService orderItemService;

	/**
	 * 租户级「司机可否自助拉未派送订单」开关。
	 * 只依赖 Mapper 与 Redis，不反向依赖本服务，无环。
	 */
	private final IOrderConfigService orderConfigService;

	/**
	 * 直接用订单 Mapper 而非 IOrderInfoService：后者已依赖本服务（下单即建配送任务），
	 * 反向注入会成环。拉单只需要按条件查订单行，Mapper 足够。
	 */
	private final OrderInfoMapper orderInfoMapper;

	private final RocketMQTemplate rocketMQTemplate;

	private static final DateTimeFormatter TRIP_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean createTaskOnPay(OrderInfo orderInfo, List<OrderItemEntity> orderItems) {
		if (orderInfo == null || !(MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())
				|| MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay()))) {
			return Boolean.FALSE;
		}
		if (CollUtil.isEmpty(orderItems)) {
			log.warn("订单[{}]明细为空，无法创建配送任务", orderInfo.getId());
			return Boolean.FALSE;
		}
		DeliveryTask existTask = getOne(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getOrderId, orderInfo.getId()));
		if (existTask != null) {
			log.info("订单[{}]配送任务已存在[{}]，幂等返回", orderInfo.getId(), existTask.getId());
			return Boolean.TRUE;
		}
		DeliveryTask task = new DeliveryTask();
		task.setTaskNo(generateTaskNo(orderInfo.getOrderNo()));
		task.setOrderId(orderInfo.getId());
		task.setOrderNo(orderInfo.getOrderNo());
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setSortNo(1);
		task.setAttemptNo(1);
		task.setVersion(0);
		task.setTenantId(orderInfo.getTenantId());
		task.setRecipientName(orderInfo.getRecipientName());
		task.setRecipientPhone(orderInfo.getRecipientPhone());
		task.setRecipientAddress(buildFullAddress(orderInfo));
		applyInternalDeliverySnapshot(task, orderInfo);
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		if (warehouseConfig != null) {
			task.setWarehouseAddress(buildWarehouseAddress(warehouseConfig));
		}
		try {
			save(task);
		}
		catch (org.springframework.dao.DuplicateKeyException exception) {
			// 唯一键 uk_delivery_task_order（tenant_id, order_id）兜底：并发重复回调只保留一个任务
			DeliveryTask concurrentTask = getOne(Wrappers.<DeliveryTask>lambdaQuery()
				.eq(DeliveryTask::getOrderId, orderInfo.getId()));
			log.info("订单[{}]并发创建配送任务命中唯一约束，幂等返回任务[{}]", orderInfo.getId(),
					concurrentTask != null ? concurrentTask.getId() : "-");
			return Boolean.TRUE;
		}

		for (OrderItemEntity orderItem : orderItems) {
			DeliveryTaskItem item = new DeliveryTaskItem();
			item.setTaskId(task.getId());
			item.setOrderItemId(orderItem.getId());
			item.setSpuName(orderItem.getSpuName());
			item.setSkuName(orderItem.getSpecsInfo());
			item.setQuantity(orderItem.getBuyQuantity());
			item.setImage(orderItem.getPicUrl());
			item.setPicked("0");
			item.setAttemptNo(1);
			item.setTenantId(orderInfo.getTenantId());
			deliveryTaskItemService.save(item);
		}
		saveLog(task.getId(), "CREATE", null, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(), 1,
				"3", null, "支付成功自动创建任务");
		log.info("订单[{}]支付成功，自动创建配送任务[{}]", orderInfo.getId(), task.getId());
		return Boolean.TRUE;
	}

	/**
	 * 内部配送（delivery_way=4）任务保存船舶、靠港、港口与购买场景快照；快照以订单数据为准。
	 */
	private void applyInternalDeliverySnapshot(DeliveryTask task, OrderInfo orderInfo) {
		task.setPurchaseScene(orderInfo.getPurchaseScene());
		if (!MallOrderConstants.DELIVERY_WAY_4.equals(orderInfo.getDeliveryWay())) {
			return;
		}
		task.setVesselId(orderInfo.getVesselId());
		task.setVesselName(orderInfo.getVesselName());
		task.setVesselCallId(orderInfo.getVesselCallId());
		task.setPortCode(orderInfo.getPortCode());
		task.setPortName(orderInfo.getPortName());
		task.setBerth(orderInfo.getBerth());
		task.setDeliveryWindowStart(orderInfo.getDeliveryWindowStart());
		task.setDeliveryWindowEnd(orderInfo.getDeliveryWindowEnd());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String assignTasks(DeliveryAssignDTO dto) {
		List<String> taskIds = dto.getTaskIds();
		if (CollUtil.isEmpty(taskIds)) {
			throw new ArynBusinessException("任务ID列表不能为空");
		}
		DeliveryStaff staff = deliveryStaffService.getById(dto.getStaffId());
		validateAssignableStaff(staff, null);
		List<DeliveryTask> tasks = list(Wrappers.<DeliveryTask>lambdaQuery()
			.in(DeliveryTask::getId, taskIds)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode()));
		if (tasks.size() != taskIds.size()) {
			throw new ArynBusinessException("存在非待派单状态的任务，无法派单");
		}
		if (tasks.stream().anyMatch(task -> StrUtil.isBlank(task.getTenantId())
				|| !Objects.equals(task.getTenantId(), staff.getTenantId()))) {
			throw new ArynBusinessException("任务与配送员不属于同一租户");
		}
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		String warehouseAddress = warehouseConfig == null ? null : buildWarehouseAddress(warehouseConfig);

		// 一个司机一辆车：新派的货并入他当前这趟车（含已出发的），不新开单
		AssignTripTarget target = resolveAssignTrip(staff, warehouseAddress, tasks.size());
		DeliveryTrip trip = target.trip();
		// 追加时顺序号接着排，把新单放在已有路线末尾，由司机再手动调整顺序
		int nextSortNo = target.created() ? 1 : nextSortNo(trip.getId());
		TaskAttachPlan plan = planTaskAttach(trip);

		LocalDateTime now = LocalDateTime.now();
		for (int i = 0; i < tasks.size(); i++) {
			DeliveryTask task = tasks.get(i);
			int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
				.eq(DeliveryTask::getId, task.getId())
				.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode())
				.set(DeliveryTask::getTripId, trip.getId())
				.set(DeliveryTask::getStaffId, staff.getId())
				.set(DeliveryTask::getStatus, plan.status())
				.set(plan.stampPickUpTime(), DeliveryTask::getPickUpTime, now)
				.set(plan.stampDepartTime(), DeliveryTask::getDepartTime, now)
				.set(DeliveryTask::getSortNo, nextSortNo + i)
				.set(DeliveryTask::getAssignTime, now)
				.set(DeliveryTask::getWarehouseAddress, warehouseAddress));
			if (updated == 0) {
				throw new ArynBusinessException("任务[" + task.getTaskNo() + "]状态已变化，派单失败");
			}
			saveLog(task.getId(), "ASSIGN", DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(),
					plan.status(), task.getAttemptNo(),
					"1", staff.getId(), "派单给[" + staff.getStaffName() + "]");
		}
		// 并入后重算趟次单数，避免任务列表按冗余列展示时与实际任务数不一致
		if (!target.created()) {
			refreshTripTaskCount(trip.getId());
		}
		// 并入「已出发」的趟次：新单落配货中，司机按清单配齐后再点一次出发，
		// 由 depart 把订单推进到待收货（见 planTaskAttach）
		log.info("批量派单成功，出车单[{}]，本次任务数[{}]，{}{}", trip.getId(), tasks.size(),
				target.created() ? "新建出车单" : "并入司机在途趟次",
				plan.reDepartRequired() ? "，需司机配货后再次出发" : "");
		sendAssignNotification(staff, tasks, trip);
		return trip.getId();
	}

	/**
	 * 任务并入趟次后的落点计划。
	 *
	 * @param status 任务落库状态
	 * @param stampPickUpTime 是否补记取货时间
	 * @param stampDepartTime 是否补记出发时间
	 * @param reDepartRequired 并入后趟次是否需要再点一次「出发」才能把这单带走
	 */
	private record TaskAttachPlan(String status, boolean stampPickUpTime, boolean stampDepartTime,
			boolean reDepartRequired) {
	}

	/**
	 * 按目标趟次的进度决定新并入任务的落点状态。
	 *
	 * <p>必须与趟次进度对齐，否则任务会被 `depart` 漏掉而永远送不出去
	 * （`depart` 只把「配货中」的任务推进到「待送达」）：
	 * <ul>
	 *   <li>趟次待配货 → 任务待取货（等司机点「开始配货」统一推进）</li>
	 *   <li>趟次配货中 → 任务配货中（可直接被 depart 带走）</li>
	 *   <li>趟次配送中 → 任务配货中，司机要按清单配货后才能再次出发</li>
	 * </ul>
	 *
	 * <p>最后一档是刻意不「直达待送达」的：车已开出去不代表货已经在车上。
	 * 派单/拉单带进来的商品明细司机必须先看到、先配齐，再由
	 * {@link DeliveryTripServiceImpl#depart} 在重复出发时把新并进的「配货中」任务
	 * 推进到「待送达」并联动订单转待收货。若在这里直推待送达，订单立即发货，
	 * 司机却从没见过这单明细，配货环节整个被跳过。
	 */
	private TaskAttachPlan planTaskAttach(DeliveryTrip trip) {
		String tripStatus = trip.getStatus();
		if (DeliveryTripStatusEnum.DELIVERING.getCode().equals(tripStatus)) {
			return new TaskAttachPlan(DeliveryTaskStatusEnum.PICKING.getCode(), true, false, true);
		}
		if (DeliveryTripStatusEnum.LOADING.getCode().equals(tripStatus)) {
			return new TaskAttachPlan(DeliveryTaskStatusEnum.PICKING.getCode(), true, false, false);
		}
		return new TaskAttachPlan(DeliveryTaskStatusEnum.WAITING_PICK.getCode(), false, false, false);
	}

	/**
	 * 租户级「司机可否自助拉未派送订单」开关。
	 *
	 * <p>默认允许：未配置或配置读取失败都按放行处理，否则配置表的任何异常都会
	 * 让司机一单也拉不了。只有显式配成 0 才收紧。
	 */
	@Override
	public boolean isDriverSelfPullUnassignedAllowed() {
		try {
			OrderConfig config = orderConfigService.getConfig();
			if (config == null || StrUtil.isBlank(config.getDriverSelfPullUnassigned())) {
				return Boolean.TRUE;
			}
			return !CommonConstants.NO.equals(config.getDriverSelfPullUnassigned());
		}
		catch (Exception ex) {
			log.warn("读取司机自助拉单开关失败，按允许处理", ex);
			return Boolean.TRUE;
		}
	}

	/**
	 * 校验某个订单是否允许被当前司机拉进趟次。
	 *
	 * <p>关掉自助拉单后，只放行「已有配送任务且已派给本人」的订单；
	 * 无任务的散单（未派送）一律拒绝。任务已派给别人（含未派单但被他人先接走）
	 * 同样拒绝 —— 司机自助拉单从来不是抢单。
	 */
	private void assertPullAllowed(OrderInfo order, DeliveryTask task, String staffId) {
		if (isDriverSelfPullUnassignedAllowed()) {
			// 开关打开时仍不允许抢别人的单
			if (task != null && StrUtil.isNotBlank(task.getStaffId()) && !staffId.equals(task.getStaffId())) {
				throw new ArynBusinessException("订单[" + order.getOrderNo() + "]已被其他配送员接单");
			}
			return;
		}
		if (task == null || !staffId.equals(task.getStaffId())) {
			throw new ArynBusinessException(
					"当前租户未开放司机自助拉单，订单[" + order.getOrderNo() + "]需由管理端派单");
		}
	}

	/**
	 * 派单目标趟次（是否新建 + 出车单实体）
	 */
	private record AssignTripTarget(DeliveryTrip trip, boolean created) {
	}

	/**
	 * 派单目标趟次：并入该司机当前这趟车（待配货/配货中/配送中），没有才新建。
	 *
	 * <p>现实里一个司机一辆车、一趟车送多个订单：货是陆续派给他、陆续装车的，
	 * 所以「是否还能并单」不该由车的出发状态决定——已出发的趟次也要能继续加单
	 * （司机回车取货或顺手捎带）。任务落点由 {@link #planTaskAttach} 按趟次进度对齐。
	 *
	 * <p>取创建时间最新的一张：司机若因历史原因仍有多个在途趟次，新的货并入最新那趟，
	 * 其余趟次由存量归并脚本或司机在配货页手动拉合。
	 * @param staff 配送员
	 * @param warehouseAddress 仓库地址快照
	 * @param assignTaskCount 本次派单的任务数（新建趟次时直接作为单数初值）
	 * @return 可并入的在途出车单，或新建的出车单
	 */
	private AssignTripTarget resolveAssignTrip(DeliveryStaff staff, String warehouseAddress, int assignTaskCount) {
		DeliveryTrip existing = deliveryTripService.getOne(Wrappers.<DeliveryTrip>lambdaQuery()
			.eq(DeliveryTrip::getStaffId, staff.getId())
			.in(DeliveryTrip::getStatus, DeliveryTripStatusEnum.WAITING_LOAD.getCode(),
					DeliveryTripStatusEnum.LOADING.getCode(), DeliveryTripStatusEnum.DELIVERING.getCode())
			.orderByDesc(DeliveryTrip::getCreateTime)
			.last("LIMIT 1"));
		if (existing != null) {
			return new AssignTripTarget(existing, false);
		}
		DeliveryTrip trip = new DeliveryTrip();
		trip.setTripNo(generateTripNo());
		trip.setStaffId(staff.getId());
		trip.setStatus(DeliveryTripStatusEnum.WAITING_LOAD.getCode());
		trip.setTaskCount(assignTaskCount);
		trip.setWarehouseAddress(warehouseAddress);
		trip.setTenantId(staff.getTenantId());
		deliveryTripService.save(trip);
		return new AssignTripTarget(trip, true);
	}

	/**
	 * 趟次内下一个可用顺序号（已有任务的 sortNo 最大值 + 1）
	 */
	private int nextSortNo(String tripId) {
		DeliveryTask last = getOne(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId)
			.orderByDesc(DeliveryTask::getSortNo)
			.last("LIMIT 1"));
		return last == null || last.getSortNo() == null ? 1 : last.getSortNo() + 1;
	}

	/**
	 * 按实际任务数回写趟次单数冗余列，供管理端列表直接展示。
	 */
	private void refreshTripTaskCount(String tripId) {
		if (StrUtil.isBlank(tripId)) {
			return;
		}
		long count = count(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTripId, tripId));
		deliveryTripService.update(Wrappers.<DeliveryTrip>lambdaUpdate()
			.eq(DeliveryTrip::getId, tripId)
			.set(DeliveryTrip::getTaskCount, (int) count));
	}

	/**
	 * 任务终结后收尾出车单：先重算单数，再按结清口径判断是否收车。
	 *
	 * <p>两个动作必须一起做：只收车不重算，管理端出车单列表的「订单数」会停留在
	 * 任务取消前的旧值（历史遗留：取消/关闭/退回确认三条路径都漏了重算）。
	 * 顺序也不能反：先重算再判收车，收车后留下的单数才是准的。
	 */
	private void settleTripAfterTaskClosed(String tripId) {
		if (StrUtil.isBlank(tripId)) {
			return;
		}
		refreshTripTaskCount(tripId);
		deliveryTripService.completeIfAllTasksSettled(tripId);
	}

	@Override
	public DeliveryTask getTaskByOrderId(String orderId) {
		if (StrUtil.isBlank(orderId)) {
			return null;
		}
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return null;
		}
		fillStaffName(List.of(task));
		return task;
	}

	@Override
	public Map<String, DeliveryTask> mapByOrderIds(List<String> orderIds) {
		if (CollUtil.isEmpty(orderIds)) {
			return Map.of();
		}
		List<DeliveryTask> tasks = list(Wrappers.<DeliveryTask>lambdaQuery()
			.in(DeliveryTask::getOrderId, orderIds));
		if (CollUtil.isEmpty(tasks)) {
			return Map.of();
		}
		fillStaffName(tasks);
		return tasks.stream()
			.filter(task -> StrUtil.isNotBlank(task.getOrderId()))
			.collect(Collectors.toMap(DeliveryTask::getOrderId, task -> task, (first, second) -> first));
	}

	@Override
	public long countPendingTasks(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return 0L;
		}
		return count(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getStaffId, staffId)
			.in(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_PICK.getCode(),
					DeliveryTaskStatusEnum.PICKING.getCode(), DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode()));
	}

	@Override
	public long countTodayDoneTasks(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return 0L;
		}
		LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
		LocalDateTime startOfNextDay = startOfDay.plusDays(1);
		// 「今日」以送达时间落在今天为准：历史累计送达不该算进今天的成绩
		return count(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getStaffId, staffId)
			.in(DeliveryTask::getStatus, DeliveryTaskStatusEnum.ARRIVED.getCode(),
					DeliveryTaskStatusEnum.SIGNED.getCode())
			.ge(DeliveryTask::getArriveTime, startOfDay)
			.lt(DeliveryTask::getArriveTime, startOfNextDay));
	}

	@Override
	public List<DeliveryCandidateOrderVO> listPullCandidates(String tripId, String staffId, String source,
			String keyword) {
		if (StrUtil.isBlank(tripId) || StrUtil.isBlank(staffId)) {
			return List.of();
		}
		boolean wantMine = StrUtil.isBlank(source) || "MINE".equalsIgnoreCase(source);
		// 「未派送订单」是租户级可关闭的能力：关掉后司机只能拉管理端已派给自己的任务。
		// 空 source（临时新增）也走同一开关，否则它会变成绕过开关的后门。
		boolean wantUnassigned = (StrUtil.isBlank(source) || "UNASSIGNED".equalsIgnoreCase(source))
				&& isDriverSelfPullUnassignedAllowed();

		// 本趟已有订单：候选列表里要排除（司机看的是「还能拉什么」）
		Set<String> inTripOrderIds = list(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId))
			.stream()
			.map(DeliveryTask::getOrderId)
			.filter(StrUtil::isNotBlank)
			.collect(Collectors.toSet());

		// 一趟车里每个订单只应有一个任务：先按订单去重，别让同名订单在候选里重复出现
		Map<String, DeliveryCandidateOrderVO> candidates = new LinkedHashMap<>();

		if (wantMine) {
			// 未完成 = 待取货/配货中/待送达；已派给当前司机但不在本趟
			List<DeliveryTask> mine = list(Wrappers.<DeliveryTask>lambdaQuery()
				.eq(DeliveryTask::getStaffId, staffId)
				.in(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_PICK.getCode(),
						DeliveryTaskStatusEnum.PICKING.getCode(), DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())
				.orderByDesc(DeliveryTask::getCreateTime));
			for (DeliveryTask task : mine) {
				if (StrUtil.isBlank(task.getOrderId()) || inTripOrderIds.contains(task.getOrderId())) {
					continue;
				}
				candidates.putIfAbsent(task.getOrderId(), fromTask(task, "MINE"));
			}
		}

		if (wantUnassigned) {
			// 未派送 = 本租户待发货、走商城/内部配送的单（可能有任务也可能没有）。
			//
			// 不能只认 pay_status=1：货到付款单的 pay_status 恒为 0（钱是送达时才收的），
			// 而它恰恰是司机必须上门的那类单。这里的「未派送」是配送语义（还没派到车上），
			// 不是付款语义，所以按待发货状态筛即可 —— 未付款的预付单根本进不到待发货。
			List<OrderInfo> orders = orderInfoMapper.selectList(Wrappers.<OrderInfo>lambdaQuery()
				.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode())
				.in(OrderInfo::getDeliveryWay, MallOrderConstants.DELIVERY_WAY_3, MallOrderConstants.DELIVERY_WAY_4)
				.orderByDesc(OrderInfo::getCreateTime));
			if (CollUtil.isNotEmpty(orders)) {
				Map<String, DeliveryTask> taskByOrderId = mapByOrderIds(orders.stream()
					.map(OrderInfo::getId)
					.toList());
				for (OrderInfo order : orders) {
					if (inTripOrderIds.contains(order.getId())) {
						continue;
					}
					DeliveryTask task = taskByOrderId.get(order.getId());
					// 已被别人接走（别人的趟次/别人在做）的单不进候选：司机自助拉单不能抢单
					if (task != null && StrUtil.isNotBlank(task.getStaffId())
							&& !staffId.equals(task.getStaffId())) {
						continue;
					}
					candidates.putIfAbsent(order.getId(), fromOrder(order, task, "UNASSIGNED"));
				}
			}
		}

		List<DeliveryCandidateOrderVO> result = new ArrayList<>(candidates.values());
		if (StrUtil.isNotBlank(keyword)) {
			String lowered = keyword.trim().toLowerCase();
			result.removeIf(candidate -> !matchesKeyword(candidate, lowered));
		}
		fillCandidateItems(result);
		return result;
	}

	/**
	 * 候选行的关键字匹配：订单号/收货人/电话，大小写不敏感。
	 */
	private boolean matchesKeyword(DeliveryCandidateOrderVO candidate, String loweredKeyword) {
		return containsIgnoreCase(candidate.getOrderNo(), loweredKeyword)
				|| containsIgnoreCase(candidate.getRecipientName(), loweredKeyword)
				|| containsIgnoreCase(candidate.getRecipientPhone(), loweredKeyword);
	}

	private boolean containsIgnoreCase(String value, String loweredKeyword) {
		return value != null && value.toLowerCase().contains(loweredKeyword);
	}

	/**
	 * 批量补齐候选行的商品摘要与件数：一次查完所有订单的明细，避免逐单查询。
	 */
	private void fillCandidateItems(List<DeliveryCandidateOrderVO> candidates) {
		if (CollUtil.isEmpty(candidates)) {
			return;
		}
		List<String> orderIds = candidates.stream().map(DeliveryCandidateOrderVO::getOrderId).toList();
		Map<String, List<OrderItemEntity>> itemsByOrderId = orderItemService
			.list(Wrappers.<OrderItemEntity>lambdaQuery().in(OrderItemEntity::getOrderId, orderIds))
			.stream()
			.collect(Collectors.groupingBy(OrderItemEntity::getOrderId));
		candidates.forEach(candidate -> {
			List<OrderItemEntity> items = itemsByOrderId.getOrDefault(candidate.getOrderId(), List.of());
			candidate.setItemCount(items.stream()
				.mapToInt(item -> item.getBuyQuantity() == null ? 0 : item.getBuyQuantity())
				.sum());
			candidate.setItems(items.stream().map(item -> {
				DeliveryCandidateItemVO row = new DeliveryCandidateItemVO();
				row.setSpuName(item.getSpuName());
				row.setSpecsInfo(item.getSpecsInfo());
				row.setQuantity(item.getBuyQuantity());
				row.setPicUrl(item.getPicUrl());
				return row;
			}).toList());
		});
	}

	/**
	 * 由已有配送任务构造候选行（「我的未完成」来源）
	 */
	private DeliveryCandidateOrderVO fromTask(DeliveryTask task, String source) {
		DeliveryCandidateOrderVO vo = new DeliveryCandidateOrderVO();
		vo.setOrderId(task.getOrderId());
		vo.setOrderNo(task.getOrderNo());
		vo.setSource(source);
		vo.setTaskId(task.getId());
		vo.setTaskStatus(task.getStatus());
		vo.setTripId(task.getTripId());
		vo.setTripNo(resolveTripNo(task.getTripId()));
		vo.setRecipientName(task.getRecipientName());
		vo.setRecipientPhone(task.getRecipientPhone());
		vo.setRecipientAddress(task.getRecipientAddress());
		vo.setVesselName(task.getVesselName());
		vo.setPortName(task.getPortName());
		vo.setBerth(task.getBerth());
		vo.setCreateTime(task.getCreateTime());
		return vo;
	}

	/**
	 * 由订单构造候选行（「未派送订单」来源）；任务可能不存在
	 */
	private DeliveryCandidateOrderVO fromOrder(OrderInfo order, DeliveryTask task, String source) {
		DeliveryCandidateOrderVO vo = new DeliveryCandidateOrderVO();
		vo.setOrderId(order.getId());
		vo.setOrderNo(order.getOrderNo());
		vo.setSource(source);
		if (task != null) {
			vo.setTaskId(task.getId());
			vo.setTaskStatus(task.getStatus());
			vo.setTripId(task.getTripId());
			vo.setTripNo(resolveTripNo(task.getTripId()));
			// 任务快照优先：船供单的船舶/泊位只存在任务快照里
			vo.setVesselName(task.getVesselName());
			vo.setPortName(task.getPortName());
			vo.setBerth(task.getBerth());
		}
		vo.setRecipientName(order.getRecipientName());
		vo.setRecipientPhone(order.getRecipientPhone());
		vo.setRecipientAddress(StrUtil.isNotBlank(task != null ? task.getRecipientAddress() : null)
				? task.getRecipientAddress()
				: buildFullAddress(order));
		vo.setPaymentPrice(order.getPaymentPrice());
		vo.setDeliveryWay(order.getDeliveryWay());
		vo.setPaymentType(order.getPaymentType());
		vo.setPayStatus(order.getPayStatus());
		vo.setCreateTime(order.getCreateTime());
		return vo;
	}

	/**
	 * 出车单号（用于告诉司机「这单现在挂在哪趟车上」）；查不到返回 null。
	 */
	private String resolveTripNo(String tripId) {
		if (StrUtil.isBlank(tripId)) {
			return null;
		}
		DeliveryTrip trip = deliveryTripService.getById(tripId);
		return trip == null ? null : trip.getTripNo();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int pullOrdersIntoTrip(String tripId, String staffId, List<String> orderIds) {
		if (CollUtil.isEmpty(orderIds)) {
			throw new ArynBusinessException("请至少选择一个订单");
		}
		DeliveryTrip trip = deliveryTripService.getById(tripId);
		if (trip == null) {
			throw new ArynBusinessException("出车单不存在");
		}
		if (!Objects.equals(trip.getStaffId(), staffId)) {
			throw new ArynBusinessException("无权操作该出车单");
		}
		// 已完成/已收车的趟次不能再加单；已出发（配送中）可以加——货陆续装车，
		// 司机回车取货或顺路捎带都属正常，落点由 planTaskAttach 对齐到「待送达」
		if (DeliveryTripStatusEnum.COMPLETED.getCode().equals(trip.getStatus())) {
			throw new ArynBusinessException("出车单已收车，无法追加订单");
		}
		TaskAttachPlan plan = planTaskAttach(trip);
		// 本趟已有的订单不重复拉
		Set<String> inTripOrderIds = list(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getTripId, tripId))
			.stream()
			.map(DeliveryTask::getOrderId)
			.filter(StrUtil::isNotBlank)
			.collect(Collectors.toSet());

		int sortNo = nextSortNo(tripId);
		LocalDateTime now = LocalDateTime.now();
		int pulled = 0;
		for (String orderId : orderIds.stream().distinct().toList()) {
			if (StrUtil.isBlank(orderId) || inTripOrderIds.contains(orderId)) {
				continue;
			}
			OrderInfo order = orderInfoMapper.selectById(orderId);
			if (order == null) {
				throw new ArynBusinessException("订单不存在：" + orderId);
			}
			if (!Objects.equals(order.getTenantId(), trip.getTenantId())) {
				throw new ArynBusinessException("订单与出车单不属于同一租户");
			}
			DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
			// 候选列表只是 UI 便利，真正的闸门在这里：关掉自助拉单后，
			// 只有「任务已派给本人」的订单能被拉，否则构造请求就能绕过开关
			assertPullAllowed(order, task, staffId);
			if (task == null) {
				// 历史订单可能缺配送任务（下单时未建/被清理）：按订单快照补建后再拉进来
				task = createTaskForOrder(order);
			}
			moveTaskIntoTrip(task, trip, staffId, sortNo, plan, now);
			sortNo++;
			pulled++;
		}
		if (pulled > 0) {
			refreshTripTaskCount(tripId);
		}
		// 拉进「已出发」的趟次：新单落配货中，司机配齐后再次出发才会转待收货
		log.info("司机[{}]把[{}]个订单拉进出车单[{}]{}", staffId, pulled, tripId,
				plan.reDepartRequired() ? "，需司机配货后再次出发" : "");
		return pulled;
	}

	/**
	 * 把已有任务改属目标趟次，并把状态对齐到本趟的进度。
	 *
	 * <p>状态对齐很关键：并入进行中的趟次时如果停在待取货，
	 * depart 只把配货中的任务带到配送中，这单永远送不出去。
	 */
	private void moveTaskIntoTrip(DeliveryTask task, DeliveryTrip trip, String staffId, int sortNo,
			TaskAttachPlan plan, LocalDateTime now) {
		String previousTripId = task.getTripId();
		String previousStatus = task.getStatus();
		// 只允许拉未完成的任务：已完成/已取消的单不该重新上车
		if (!DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode().equals(previousStatus)
				&& !DeliveryTaskStatusEnum.WAITING_PICK.getCode().equals(previousStatus)
				&& !DeliveryTaskStatusEnum.PICKING.getCode().equals(previousStatus)) {
			throw new ArynBusinessException("订单[" + task.getOrderNo() + "]当前状态不允许加入出车单");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, task.getId())
			.set(DeliveryTask::getTripId, trip.getId())
			.set(DeliveryTask::getStaffId, staffId)
			.set(DeliveryTask::getStatus, plan.status())
			.set(plan.stampPickUpTime(), DeliveryTask::getPickUpTime, now)
			.set(plan.stampDepartTime(), DeliveryTask::getDepartTime, now)
			.set(DeliveryTask::getSortNo, sortNo)
			.set(DeliveryTask::getAssignTime, now)
			.set(DeliveryTask::getWarehouseAddress, trip.getWarehouseAddress()));
		if (updated == 0) {
			throw new ArynBusinessException("订单[" + task.getOrderNo() + "]状态已变化，拉单失败");
		}
		saveLog(task.getId(), "PULL_INTO_TRIP", previousStatus, plan.status(),
				task.getAttemptNo(), "2", staffId, "拉入出车单[" + trip.getTripNo() + "]");
		// 从别的趟次挪过来的：原趟次少一单，单数要重算，全空了就地收车
		if (StrUtil.isNotBlank(previousTripId) && !previousTripId.equals(trip.getId())) {
			refreshTripTaskCount(previousTripId);
			deliveryTripService.completeIfAllTasksSettled(previousTripId);
		}
	}

	/**
	 * 为历史缺任务的订单按订单快照补建配送任务与取货明细（状态待派单，随后由拉单动作接管）。
	 */
	private DeliveryTask createTaskForOrder(OrderInfo order) {
		List<OrderItemEntity> orderItems = orderItemService
			.list(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getOrderId, order.getId()));
		if (CollUtil.isEmpty(orderItems)) {
			throw new ArynBusinessException("订单[" + order.getOrderNo() + "]没有商品明细，无法加入出车单");
		}
		if (!(MallOrderConstants.DELIVERY_WAY_3.equals(order.getDeliveryWay())
				|| MallOrderConstants.DELIVERY_WAY_4.equals(order.getDeliveryWay()))) {
			throw new ArynBusinessException("订单[" + order.getOrderNo() + "]不是商城配送订单");
		}
		DeliveryTask task = new DeliveryTask();
		task.setTaskNo(generateTaskNo(order.getOrderNo()));
		task.setOrderId(order.getId());
		task.setOrderNo(order.getOrderNo());
		task.setStatus(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode());
		task.setSortNo(1);
		task.setAttemptNo(1);
		task.setVersion(0);
		task.setRecipientName(order.getRecipientName());
		task.setRecipientPhone(order.getRecipientPhone());
		task.setRecipientAddress(buildFullAddress(order));
		task.setTenantId(order.getTenantId());
		applyInternalDeliverySnapshot(task, order);
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		if (warehouseConfig != null) {
			task.setWarehouseAddress(buildWarehouseAddress(warehouseConfig));
		}
		save(task);
		for (OrderItemEntity orderItem : orderItems) {
			DeliveryTaskItem item = new DeliveryTaskItem();
			item.setTaskId(task.getId());
			item.setOrderItemId(orderItem.getId());
			item.setSpuName(orderItem.getSpuName());
			item.setSkuName(orderItem.getSpecsInfo());
			item.setQuantity(orderItem.getBuyQuantity());
			item.setImage(orderItem.getPicUrl());
			item.setPicked("0");
			item.setAttemptNo(1);
			item.setTenantId(order.getTenantId());
			deliveryTaskItemService.save(item);
		}
		saveLog(task.getId(), "CREATE", null, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(), 1,
				"2", null, "司机拉单时补建配送任务");
		return task;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String assignByOrderId(String orderId, String staffId) {
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery()
			.eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在，无法派单");
		}
		if (!DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("配送任务已派单或已结束，请到配送任务页查看");
		}
		DeliveryAssignDTO dto = new DeliveryAssignDTO();
		dto.setTaskIds(List.of(task.getId()));
		dto.setStaffId(staffId);
		return assignTasks(dto);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reassign(String taskId, String staffId) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode().equals(task.getStatus())
				&& !DeliveryTaskStatusEnum.EXCEPTION.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("只允许待派单或异常状态的任务改派");
		}
		DeliveryStaff staff = deliveryStaffService.getById(staffId);
		validateAssignableStaff(staff, task.getTenantId());
		// 改派要一并换趟次：工作台按「司机的趟次」组织，只换 staffId 而把 tripId
		// 留在原司机的车上，这单在新司机的工作台根本不会出现，原司机的车上又多出一单
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		String warehouseAddress = warehouseConfig == null ? null : buildWarehouseAddress(warehouseConfig);
		String previousTripId = task.getTripId();
		AssignTripTarget target = resolveAssignTrip(staff, warehouseAddress, 1);
		DeliveryTrip trip = target.trip();
		int nextSortNo = target.created() ? 1 : nextSortNo(trip.getId());
		TaskAttachPlan plan = planTaskAttach(trip);
		LocalDateTime now = LocalDateTime.now();

		int newAttemptNo = (task.getAttemptNo() == null ? 1 : task.getAttemptNo()) + 1;
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.eq(DeliveryTask::getVersion, task.getVersion())
			.set(DeliveryTask::getStaffId, staffId)
			.set(DeliveryTask::getTripId, trip.getId())
			.set(DeliveryTask::getSortNo, nextSortNo)
			// 落点与趟次进度对齐，否则 depart 不会把它带去配送
			.set(DeliveryTask::getStatus, plan.status())
			.set(plan.stampPickUpTime(), DeliveryTask::getPickUpTime, now)
			.set(plan.stampDepartTime(), DeliveryTask::getDepartTime, now)
			.set(DeliveryTask::getAttemptNo, newAttemptNo)
			.set(DeliveryTask::getAssignTime, now)
			.set(DeliveryTask::getWarehouseAddress, warehouseAddress)
			.set(DeliveryTask::getExceptionReason, null)
			.set(DeliveryTask::getExceptionDesc, null));
		if (updated == 0) {
			throw new ArynBusinessException("改派失败，任务已变化");
		}
		deliveryTaskItemService.update(Wrappers.<DeliveryTaskItem>lambdaUpdate()
			.eq(DeliveryTaskItem::getTaskId, taskId)
			.set(DeliveryTaskItem::getPicked, "0")
			.set(DeliveryTaskItem::getPickedTime, null)
			.set(DeliveryTaskItem::getAttemptNo, newAttemptNo));
		// 两趟车的单数都要重算：原趟次少一单，新趟次多一单
		if (StrUtil.isNotBlank(previousTripId) && !previousTripId.equals(trip.getId())) {
			refreshTripTaskCount(previousTripId);
			deliveryTripService.completeIfAllTasksSettled(previousTripId);
		}
		refreshTripTaskCount(trip.getId());
		saveLog(taskId, "REASSIGN", task.getStatus(), plan.status(),
				newAttemptNo, "1", staffId, "改派给[" + staff.getStaffName() + "]");
		// 改派进「已出发」的趟次：这单落配货中，司机配齐后再出发才转待收货
		return Boolean.TRUE;
	}

	@Override
	public DeliveryTask getTaskDetail(String id) {
		DeliveryTask task = getById(id);
		if (task == null) {
			return null;
		}
		List<DeliveryTaskItem> items = deliveryTaskItemService.listByTaskId(id);
		deliveryTaskItemService.fillCategoryName(items);
		task.setItemList(items);
		fillStaffName(List.of(task));
		return task;
	}

	@Override
	public IPage<DeliveryTask> pageWithStaffName(Page<DeliveryTask> page, Wrapper<DeliveryTask> wrapper) {
		IPage<DeliveryTask> result = page(page, wrapper);
		fillStaffName(result.getRecords());
		return result;
	}

	@Override
	public void fillStaffName(List<DeliveryTask> tasks) {
		if (CollUtil.isEmpty(tasks)) {
			return;
		}
		Map<String, String> names = deliveryStaffService.mapStaffNames(tasks.stream()
			.map(DeliveryTask::getStaffId)
			.toList());
		if (names.isEmpty()) {
			return;
		}
		tasks.forEach(task -> task.setStaffName(names.get(task.getStaffId())));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean arrive(String taskId, String staffId) {
		return arriveWithEvidence(taskId, staffId, null, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean arriveWithEvidence(String taskId, String staffId, List<String> materialIds, String remark) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!task.getStaffId().equals(staffId)) {
			throw new ArynBusinessException("无权操作该任务");
		}
		return markArrived(task, materialIds, remark, staffId, "2", "送达确认");
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean backfillArriveByAdmin(String taskId, List<String> materialIds, String remark, String operatorId) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		// 管理端补录仅用于「司机已送达却漏点」，必须任务已进入待送达；
		// 不提供从待派单/取货中直达送达的通道，避免绕过配送流程。
		if (!DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("仅待送达的任务可由管理端补录送达凭证");
		}
		return markArrived(task, materialIds, remark, operatorId, "3", "管理端补录送达");
	}

	/**
	 * 送达落库的公共内核：司机送达与管理端补录共用，凭证要求一致（1-6 张）。
	 * @param operatorType 操作者类型：2 配送员；3 管理端
	 */
	private boolean markArrived(DeliveryTask task, List<String> materialIds, String remark,
			String operatorId, String operatorType, String logRemark) {
		String taskId = task.getId();
		if (!DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("当前任务状态不允许送达");
		}
		if (CollUtil.isEmpty(materialIds) || materialIds.size() > 6) {
			throw new ArynBusinessException("送达凭证图片数量必须为1至6张");
		}
		if (materialIds.stream().anyMatch(StrUtil::isBlank)
				|| materialIds.stream().distinct().count() != materialIds.size()) {
			throw new ArynBusinessException("送达凭证图片无效或重复");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.ARRIVED.getCode())
			.set(DeliveryTask::getArriveTime, now)
			.set(DeliveryTask::getRemark, remark));
		if (updated == 0) {
			throw new ArynBusinessException("任务状态已变化，无法送达");
		}
		saveEvidenceWithUrl(taskId, task, "1", materialIds, operatorId);
		saveLog(taskId, "ARRIVE", DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode(),
				DeliveryTaskStatusEnum.ARRIVED.getCode(), task.getAttemptNo(),
				operatorType, operatorId, logRemark);
		// 一单送达后本趟车可能只剩已送达任务：出车单不能等客户签收才结束，
		// 否则司机的「当前出车单」永远停在配送中，下一趟车也派不出来
		deliveryTripService.completeIfAllTasksSettled(task.getTripId());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean signOnReceive(String orderId) {
		if (StrUtil.isBlank(orderId)) {
			return Boolean.FALSE;
		}
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return Boolean.FALSE;
		}
		if (DeliveryTaskStatusEnum.SIGNED.getCode().equals(task.getStatus())) {
			return Boolean.TRUE;
		}
		// 只允许在「已送达」之后签收。历史实现曾把「待送达(4)」也当作可签收，
		// 那会让客户在司机尚未点送达时就确认收货——与送达守卫相矛盾，已移除该兼容路径。
		if (!DeliveryTaskStatusEnum.ARRIVED.getCode().equals(task.getStatus())) {
			log.warn("订单[{}]签收联动失败，配送任务状态[{}]不是已送达", orderId, task.getStatus());
			return Boolean.FALSE;
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, task.getId())
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.ARRIVED.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.SIGNED.getCode())
			.set(DeliveryTask::getSignTime, now)
			.set(DeliveryTask::getArriveTime, task.getArriveTime() == null ? now : task.getArriveTime()));
		if (updated == 0) {
			log.warn("订单[{}]签收联动失败，任务状态不匹配", orderId);
			return Boolean.FALSE;
		}
		// 签收只影响订单域；出车单是否收车统一按任务结清口径判定
		if (StrUtil.isNotBlank(task.getTripId())) {
			deliveryTripService.completeIfAllTasksSettled(task.getTripId());
		}
		saveLog(task.getId(), "SIGN", DeliveryTaskStatusEnum.ARRIVED.getCode(),
				DeliveryTaskStatusEnum.SIGNED.getCode(), task.getAttemptNo(),
				"3", null, "客户确认收货");
		return Boolean.TRUE;
	}

	/**
	 * 派单/改派前校验配送员仍属于当前租户且处于可接单状态。
	 */
	private void validateAssignableStaff(DeliveryStaff staff, String taskTenantId) {
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		String currentTenantId = ArynTenantContextHolder.getTenantId();
		String expectedTenantId = StrUtil.isBlank(taskTenantId) ? currentTenantId : taskTenantId;
		if (StrUtil.isBlank(staff.getTenantId())
				|| (StrUtil.isNotBlank(expectedTenantId) && !expectedTenantId.equals(staff.getTenantId()))) {
			throw new ArynBusinessException("配送员不属于当前租户");
		}
		if (StrUtil.isBlank(staff.getUserId())) {
			throw new ArynBusinessException("配送员未关联有效账号");
		}
		if (DeliveryStaffStatusEnum.OFFLINE.getCode().equals(staff.getStatus())) {
			throw new ArynBusinessException("配送员当前不可接单");
		}
	}

	@Override
	public DeliveryProgressVO getProgress(String orderId) {
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return null;
		}
		DeliveryProgressVO vo = new DeliveryProgressVO();
		vo.setOrderId(task.getOrderId());
		vo.setTaskId(task.getId());
		vo.setTaskNo(task.getTaskNo());
		vo.setStatus(task.getStatus());
		vo.setStatusDesc(DeliveryTaskStatusEnum.getValue(task.getStatus()));
		vo.setAssignTime(task.getAssignTime());
		vo.setPickUpTime(task.getPickUpTime());
		vo.setDepartTime(task.getDepartTime());
		vo.setArriveTime(task.getArriveTime());
		vo.setSignTime(task.getSignTime());
		vo.setRecipientName(task.getRecipientName());
		vo.setRecipientAddress(task.getRecipientAddress());
		if (StrUtil.isNotBlank(task.getTripId())) {
			DeliveryTrip trip = deliveryTripService.getById(task.getTripId());
			if (trip != null) {
				vo.setTripNo(trip.getTripNo());
			}
		}
		if (StrUtil.isNotBlank(task.getStaffId())) {
			DeliveryStaff staff = deliveryStaffService.getById(task.getStaffId());
			if (staff != null) {
				vo.setStaffName(staff.getStaffName());
				vo.setStaffPhone(staff.getStaffPhone());
			}
		}
		vo.setNodes(buildProgressNodes(task));
		// 买家侧凭证可见：只回 URL 不回内部 ID，送达前的空列表让客户端整块隐藏
		vo.setEvidenceUrls(listArriveEvidenceUrls(task.getId()));
		return vo;
	}

	/**
	 * 任务的送达凭证 URL（仅 evidenceType=1，按尝试号+序号排序），供客户端订单详情展示。
	 */
	private List<String> listArriveEvidenceUrls(String taskId) {
		return listEvidence(taskId).stream()
			.filter(evidence -> "1".equals(evidence.getEvidenceType()))
			.map(DeliveryEvidence::getMaterialUrl)
			.filter(StrUtil::isNotBlank)
			.collect(Collectors.toList());
	}

	/**
	 * 构建客户端配送进度时间线。
	 *
	 * <p>节点顺序与配送任务状态机一致；异常/退回/取消等非正常路径单独呈现，
	 * 避免客户端把异常订单显示成「正在配送」。
	 */
	private List<DeliveryProgressNode> buildProgressNodes(DeliveryTask task) {
		String status = task.getStatus();
		List<DeliveryProgressNode> nodes = new ArrayList<>();
		nodes.add(progressNode(DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(), "已下单，等待派单",
				task.getAssignTime(), status));
		nodes.add(progressNode(DeliveryTaskStatusEnum.WAITING_PICK.getCode(), "已派单，等待取货",
				task.getAssignTime(), status));
		nodes.add(progressNode(DeliveryTaskStatusEnum.PICKING.getCode(), "仓库配货中",
				task.getPickUpTime(), status));
		nodes.add(progressNode(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode(), "已出发，配送中",
				task.getDepartTime(), status));
		nodes.add(progressNode(DeliveryTaskStatusEnum.ARRIVED.getCode(), "已送达，等待签收",
				task.getArriveTime(), status));
		nodes.add(progressNode(DeliveryTaskStatusEnum.SIGNED.getCode(), "已签收",
				task.getSignTime(), status));

		if (DeliveryTaskStatusEnum.EXCEPTION.getCode().equals(status)) {
			nodes.add(exceptionNode("配送异常", task.getExceptionTime(), task.getExceptionDesc()));
		}
		else if (DeliveryTaskStatusEnum.RETURN_PENDING.getCode().equals(status)) {
			nodes.add(exceptionNode("待退回仓库", task.getReturnPendingTime(), task.getExceptionDesc()));
		}
		else if (DeliveryTaskStatusEnum.CANCELED.getCode().equals(status)) {
			nodes.add(exceptionNode("配送已取消", task.getCloseTime(), null));
		}
		return nodes;
	}

	/**
	 * 生成一个时间线节点：已完成（含当前节点）标记 done，处于当前状态标记 active。
	 */
	private DeliveryProgressNode progressNode(String nodeStatus, String name, LocalDateTime time, String currentStatus) {
		DeliveryProgressNode node = new DeliveryProgressNode();
		node.setStatus(nodeStatus);
		node.setName(name);
		node.setTime(time);
		int current = statusOrder(currentStatus);
		int target = statusOrder(nodeStatus);
		boolean reached = current >= 0 && target >= 0 && target <= current;
		node.setDone(reached && time != null);
		node.setActive(nodeStatus.equals(currentStatus));
		return node;
	}

	private DeliveryProgressNode exceptionNode(String name, LocalDateTime time, String desc) {
		DeliveryProgressNode node = new DeliveryProgressNode();
		node.setName(name);
		node.setTime(time);
		node.setDone(time != null);
		node.setActive(Boolean.TRUE);
		return node;
	}

	/**
	 * 主线状态顺序；异常/退回/取消不参与时间线推进计算。
	 */
	private int statusOrder(String status) {
		if (DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode().equals(status)) {
			return 0;
		}
		if (DeliveryTaskStatusEnum.WAITING_PICK.getCode().equals(status)) {
			return 1;
		}
		if (DeliveryTaskStatusEnum.PICKING.getCode().equals(status)) {
			return 2;
		}
		if (DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(status)) {
			return 3;
		}
		if (DeliveryTaskStatusEnum.ARRIVED.getCode().equals(status)) {
			return 4;
		}
		if (DeliveryTaskStatusEnum.SIGNED.getCode().equals(status)) {
			return 5;
		}
		return -1;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean cancel(String taskId) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getCloseTime, LocalDateTime.now()));
		if (updated == 0) {
			throw new ArynBusinessException("取消任务失败");
		}
		saveLog(taskId, "CANCEL", task.getStatus(), DeliveryTaskStatusEnum.CANCELED.getCode(),
				task.getAttemptNo(), "3", null, "取消任务");
		settleTripAfterTaskClosed(task.getTripId());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean cancelByOrderId(String orderId) {
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return Boolean.FALSE;
		}
		if (DeliveryTaskStatusEnum.CANCELED.getCode().equals(task.getStatus())) {
			return Boolean.TRUE;
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, task.getId())
			.ne(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getCloseTime, LocalDateTime.now()));
		if (updated > 0) {
			saveLog(task.getId(), "CANCEL", task.getStatus(), DeliveryTaskStatusEnum.CANCELED.getCode(),
					task.getAttemptNo(), "3", null, "退款完成自动关闭任务");
			settleTripAfterTaskClosed(task.getTripId());
		}
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean cancelWaitingAssignByOrderId(String orderId) {
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return Boolean.FALSE;
		}
		if (DeliveryTaskStatusEnum.CANCELED.getCode().equals(task.getStatus())) {
			return Boolean.TRUE;
		}
		if (!DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("配送已安排，无法取消");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, task.getId())
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getCloseTime, LocalDateTime.now()));
		if (updated == 0) {
			throw new ArynBusinessException("配送已安排，无法取消");
		}
		saveLog(task.getId(), "CANCEL", task.getStatus(), DeliveryTaskStatusEnum.CANCELED.getCode(),
				task.getAttemptNo(), "3", null, "货到付款订单取消自动关闭任务");
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean reportException(String taskId, String staffId, String reasonCode, String reasonDesc,
			List<String> materialIds) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!task.getStaffId().equals(staffId)) {
			throw new ArynBusinessException("无权操作该任务");
		}
		if (!DeliveryTaskStatusEnum.WAITING_PICK.getCode().equals(task.getStatus())
				&& !DeliveryTaskStatusEnum.PICKING.getCode().equals(task.getStatus())
				&& !DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("当前任务状态不允许上报异常");
		}
		if (StrUtil.isBlank(reasonDesc)) {
			throw new ArynBusinessException("异常说明必填");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.eq(DeliveryTask::getStaffId, staffId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.EXCEPTION.getCode())
			.set(DeliveryTask::getExceptionTime, now)
			.set(DeliveryTask::getExceptionReason, reasonCode)
			.set(DeliveryTask::getExceptionDesc, reasonDesc));
		if (updated == 0) {
			throw new ArynBusinessException("任务状态已变化，异常上报失败");
		}
		if (CollUtil.isNotEmpty(materialIds)) {
			saveEvidenceWithUrl(taskId, task, "2", materialIds, staffId);
		}
		saveLog(taskId, "EXCEPTION", task.getStatus(), DeliveryTaskStatusEnum.EXCEPTION.getCode(),
				task.getAttemptNo(), "2", staffId, "异常上报[" + reasonCode + "]" + reasonDesc);
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean returnPending(String taskId) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(task.getStatus())
				&& !DeliveryTaskStatusEnum.EXCEPTION.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("只允许待送达或异常状态的任务进入待退回");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.RETURN_PENDING.getCode())
			.set(DeliveryTask::getReturnPendingTime, LocalDateTime.now()));
		if (updated == 0) {
			throw new ArynBusinessException("置为待退回失败");
		}
		saveLog(taskId, "RETURN_PENDING", task.getStatus(), DeliveryTaskStatusEnum.RETURN_PENDING.getCode(),
				task.getAttemptNo(), "1", null, "置为待退回");
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean returnConfirm(String taskId, String remark) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!DeliveryTaskStatusEnum.RETURN_PENDING.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("只允许待退回状态的任务确认退回");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getReturnConfirmTime, LocalDateTime.now())
			.set(DeliveryTask::getCloseTime, LocalDateTime.now())
			.set(DeliveryTask::getRemark, remark));
		if (updated == 0) {
			throw new ArynBusinessException("确认退回失败");
		}
		saveLog(taskId, "RETURN_CONFIRM", DeliveryTaskStatusEnum.RETURN_PENDING.getCode(),
				DeliveryTaskStatusEnum.CANCELED.getCode(), task.getAttemptNo(),
				"1", null, "确认商品退回仓库[" + remark + "]");
		settleTripAfterTaskClosed(task.getTripId());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean close(String taskId, String reason) {
		DeliveryTask task = getById(taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		if (!DeliveryTaskStatusEnum.EXCEPTION.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("只允许异常状态的任务关闭");
		}
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode())
			.set(DeliveryTask::getCloseTime, LocalDateTime.now())
			.set(DeliveryTask::getRemark, reason));
		if (updated == 0) {
			throw new ArynBusinessException("关闭任务失败");
		}
		saveLog(taskId, "CLOSE", task.getStatus(), DeliveryTaskStatusEnum.CANCELED.getCode(),
				task.getAttemptNo(), "1", null, "关闭异常任务[" + reason + "]");
		settleTripAfterTaskClosed(task.getTripId());
		return Boolean.TRUE;
	}

	@Override
	public List<DeliveryEvidence> listEvidence(String taskId) {
		List<DeliveryEvidence> list = deliveryEvidenceService.list(Wrappers.<DeliveryEvidence>lambdaQuery()
			.eq(DeliveryEvidence::getTaskId, taskId)
			.orderByAsc(DeliveryEvidence::getSortNo));
		backfillEvidenceUrls(taskId, list);
		return list;
	}

	/**
	 * 送达/异常凭证按素材ID落库，同时快照素材访问URL，供管理端与小程序直接渲染；
	 * 素材服务不可用时凭证先行落库，URL由 {@link #backfillEvidenceUrls} 在读取侧兜底。
	 */
	private void saveEvidenceWithUrl(String taskId, DeliveryTask task, String evidenceType,
			List<String> materialIds, String uploadBy) {
		Map<String, String> urlMap = mapMaterialUrls(taskId, materialIds);
		for (int i = 0; i < materialIds.size(); i++) {
			DeliveryEvidence evidence = new DeliveryEvidence();
			evidence.setTaskId(taskId);
			evidence.setAttemptNo(task.getAttemptNo());
			evidence.setEvidenceType(evidenceType);
			evidence.setMaterialId(materialIds.get(i));
			evidence.setMaterialUrl(urlMap.get(materialIds.get(i)));
			evidence.setSortNo(i + 1);
			evidence.setUploadBy(uploadBy);
			evidence.setTenantId(task.getTenantId());
			deliveryEvidenceService.save(evidence);
		}
	}

	private Map<String, String> mapMaterialUrls(String taskId, List<String> materialIds) {
		try {
			Map<String, String> urlMap = remoteMaterialService.mapUrlByIds(materialIds);
			return urlMap == null ? Collections.emptyMap() : urlMap;
		}
		catch (Exception e) {
			log.warn("任务[{}]查询素材URL失败，凭证仅记录素材ID", taskId, e);
			return Collections.emptyMap();
		}
	}

	/**
	 * 存量凭证行只存了素材ID未存URL快照，读取时按素材ID回填，避免两端渲染空图。
	 */
	private void backfillEvidenceUrls(String taskId, List<DeliveryEvidence> list) {
		List<String> missingUrlIds = list.stream()
			.filter(evidence -> StrUtil.isBlank(evidence.getMaterialUrl())
					&& StrUtil.isNotBlank(evidence.getMaterialId()))
			.map(DeliveryEvidence::getMaterialId)
			.distinct()
			.collect(Collectors.toList());
		if (missingUrlIds.isEmpty()) {
			return;
		}
		Map<String, String> urlMap = mapMaterialUrls(taskId, missingUrlIds);
		if (urlMap.isEmpty()) {
			return;
		}
		list.forEach(evidence -> {
			if (StrUtil.isBlank(evidence.getMaterialUrl())) {
				evidence.setMaterialUrl(urlMap.get(evidence.getMaterialId()));
			}
		});
	}

	@Override
	public List<DeliveryTaskLog> listLogs(String taskId) {
		return deliveryTaskLogService.list(Wrappers.<DeliveryTaskLog>lambdaQuery()
			.eq(DeliveryTaskLog::getTaskId, taskId)
			.orderByAsc(DeliveryTaskLog::getCreateTime));
	}

	private void saveLog(String taskId, String action, String fromStatus, String toStatus,
			Integer attemptNo, String operatorType, String operatorId, String reasonDesc) {
		DeliveryTaskLog log = new DeliveryTaskLog();
		log.setTaskId(taskId);
		log.setAction(action);
		log.setFromStatus(fromStatus);
		log.setToStatus(toStatus);
		log.setAttemptNo(attemptNo);
		log.setOperatorType(operatorType);
		log.setOperatorId(operatorId);
		log.setReasonDesc(reasonDesc);
		deliveryTaskLogService.save(log);
	}

	private void sendAssignNotification(DeliveryStaff staff, List<DeliveryTask> tasks, DeliveryTrip trip) {
		try {
			for (DeliveryTask task : tasks) {
				MessageSendCommand command = new MessageSendCommand();
				command.setEventId("delivery-assigned:" + task.getId() + ":" + task.getAttemptNo());
				command.setTenantId(staff.getTenantId());
				command.setRecipientType("SYS_USER");
				command.setRecipientId(staff.getUserId());
				command.setRecipientName(staff.getStaffName());
				command.setCategory("DELIVERY_TASK");
				command.setTitle("配送任务派单通知");
				command.setSummary("您有新的配送任务[" + task.getTaskNo() + "]，请及时处理");
				command.setContent("出车单号：" + trip.getTripNo() + "，任务号：" + task.getTaskNo()
						+ "，订单号：" + task.getOrderNo() + "，收货人：" + task.getRecipientName());
			command.setBizType("ORDER_DELIVERY_TASK");
			command.setBizId(task.getId());
			command.setJumpPayload("/delivery/task-detail?id=" + task.getId());
			command.setChannels(java.util.List.of("IN_APP", "WECHAT_SUBSCRIBE"));
			rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
			}
		}
		catch (Exception e) {
			log.warn("派单通知发送失败，不影响派单事实: {}", e.getMessage());
		}
	}

	private String generateTaskNo(String orderNo) {
		return "DT" + LocalDateTime.now().format(TRIP_NO_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 6);
	}

	private String generateTripNo() {
		return "TR" + LocalDateTime.now().format(TRIP_NO_FORMAT) + IdUtil.fastSimpleUUID().substring(0, 6);
	}

	private String buildFullAddress(OrderInfo orderInfo) {
		StringBuilder sb = new StringBuilder();
		if (StrUtil.isNotBlank(orderInfo.getRecipientProvince())) {
			sb.append(orderInfo.getRecipientProvince());
		}
		if (StrUtil.isNotBlank(orderInfo.getRecipientCity())) {
			sb.append(orderInfo.getRecipientCity());
		}
		if (StrUtil.isNotBlank(orderInfo.getRecipientArea())) {
			sb.append(orderInfo.getRecipientArea());
		}
		if (StrUtil.isNotBlank(orderInfo.getRecipientAddress())) {
			sb.append(orderInfo.getRecipientAddress());
		}
		return sb.toString();
	}

	private String buildWarehouseAddress(DeliveryWarehouseConfig config) {
		StringBuilder sb = new StringBuilder();
		if (StrUtil.isNotBlank(config.getProvince())) {
			sb.append(config.getProvince());
		}
		if (StrUtil.isNotBlank(config.getCity())) {
			sb.append(config.getCity());
		}
		if (StrUtil.isNotBlank(config.getArea())) {
			sb.append(config.getArea());
		}
		if (StrUtil.isNotBlank(config.getAddress())) {
			sb.append(config.getAddress());
		}
		return sb.toString();
	}

}
