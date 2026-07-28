package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryCompleteRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryExceptionRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryItemCheckRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryPickupRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryEvidence;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskItem;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.api.delivery.enums.DeliveryEvidenceTypeEnum;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskActionEnum;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskStaffVO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryEvidenceMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.upms.api.remote.RemoteMaterialAccessService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

/** 配送员商城配送履约服务。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryTaskStaffService {

	private final OrderDeliveryTaskMapper taskMapper;
	private final OrderDeliveryTaskItemMapper taskItemMapper;
	private final OrderDeliveryEvidenceMapper evidenceMapper;
	private final OrderDeliveryTaskLogMapper logMapper;
	private final OrderInfoMapper orderInfoMapper;
	private final OrderItemMapper orderItemMapper;
	private final DeliveryTaskTransitionPolicy transitionPolicy;

	@DubboReference
	private final RemoteMaterialAccessService materialAccessService;

	private final DeliveryEvidenceBindingService bindingService;

	public IPage<DeliveryTaskStaffVO> page(Page<OrderDeliveryTask> page, String status) {
		ArynUser staff = currentStaff();
		var wrapper = Wrappers.<OrderDeliveryTask>lambdaQuery()
			.eq(OrderDeliveryTask::getTenantId, staff.getTenantId())
			.eq(OrderDeliveryTask::getAssigneeId, staff.getUserId())
			.eq(StringUtils.hasText(status), OrderDeliveryTask::getStatus, status)
			.orderByDesc(OrderDeliveryTask::getUpdateTime);
		return taskMapper.selectPage(page, wrapper).convert(task -> toView(task, false));
	}

	public DeliveryTaskStaffVO get(String taskId) {
		ArynUser staff = currentStaff();
		return toView(requireTask(staff, taskId), true);
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean startPicking(String taskId, DeliveryPickupRequest request) {
		ArynUser staff = currentStaff();
		validateRequest(request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		OrderDeliveryTask task = requireTask(staff, taskId);
		if (alreadyProcessed(staff, task, DeliveryTaskActionEnum.START_PICKING, request.getRequestId())) {
			return true;
		}
		DeliveryTaskStatusEnum from = statusOf(task);
		requireTransition(from, DeliveryTaskStatusEnum.PICKING);
		LocalDateTime now = LocalDateTime.now();
		requireUpdated(taskMapper.updateStaffStatus(staff.getTenantId(), staff.getUserId(), taskId, from.name(),
			DeliveryTaskStatusEnum.PICKING.name(), request.getVersion(), now, "pickingStartedAt"));
		appendLog(task, DeliveryTaskActionEnum.START_PICKING, from, DeliveryTaskStatusEnum.PICKING,
			staff, request.getRequestId(), null, now);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean checkItem(String taskId, String itemId, DeliveryItemCheckRequest request) {
		ArynUser staff = currentStaff();
		validateRequest(request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		if (request.getChecked() == null || !StringUtils.hasText(itemId)) {
			throw new ArynBusinessException("商品核对参数不完整");
		}
		OrderDeliveryTask task = requireTask(staff, taskId);
		if (alreadyProcessed(staff, task, DeliveryTaskActionEnum.CHECK_ITEM, request.getRequestId())) {
			return true;
		}
		DeliveryTaskStatusEnum status = statusOf(task);
		if (status != DeliveryTaskStatusEnum.PICKING) {
			throw new ArynBusinessException("当前配送任务状态不允许核对商品");
		}
		LocalDateTime now = LocalDateTime.now();
		requireUpdated(taskMapper.updateStaffStatus(staff.getTenantId(), staff.getUserId(), taskId, status.name(),
			status.name(), request.getVersion(), now, null));
		String checked = request.getChecked() ? CommonConstants.YES : CommonConstants.NO;
		if (taskItemMapper.updateChecked(staff.getTenantId(), taskId, itemId, task.getAttemptNo(), checked,
				staff.getUserId()) != 1) {
			throw new ArynBusinessException("配送商品不存在或已变化");
		}
		appendLog(task, DeliveryTaskActionEnum.CHECK_ITEM, status, status, staff, request.getRequestId(),
			"商品核对状态：" + checked, now);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean pickup(String taskId, DeliveryPickupRequest request) {
		ArynUser staff = currentStaff();
		validateRequest(request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		OrderDeliveryTask task = requireTask(staff, taskId);
		if (alreadyProcessed(staff, task, DeliveryTaskActionEnum.PICKUP, request.getRequestId())) {
			return true;
		}
		DeliveryTaskStatusEnum from = statusOf(task);
		requireTransition(from, DeliveryTaskStatusEnum.DELIVERING);
		if (taskItemMapper.countUnchecked(staff.getTenantId(), taskId, task.getAttemptNo()) > 0) {
			throw new ArynBusinessException("仍有商品未完成核对");
		}
		LocalDateTime now = LocalDateTime.now();
		requireUpdated(taskMapper.updateStaffStatus(staff.getTenantId(), staff.getUserId(), taskId, from.name(),
			DeliveryTaskStatusEnum.DELIVERING.name(), request.getVersion(), now, "pickedUpAt"));
		if (orderInfoMapper.markMallDeliveryPickedUp(staff.getTenantId(), task.getOrderId(), now,
				staff.getUserId()) != 1) {
			throw new ArynBusinessException("订单取货状态更新失败，请重试");
		}
		if (orderItemMapper.markMallDeliveryShipped(staff.getTenantId(), task.getOrderId()) <= 0) {
			throw new ArynBusinessException("订单商品取货状态更新失败，请重试");
		}
		appendLog(task, DeliveryTaskActionEnum.PICKUP, from, DeliveryTaskStatusEnum.DELIVERING,
			staff, request.getRequestId(), null, now);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean complete(String taskId, DeliveryCompleteRequest request) {
		ArynUser staff = currentStaff();
		validateRequest(request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		validateMaterialIds(request.getMaterialIds(), true);
		OrderDeliveryTask task = requireTask(staff, taskId);
		if (alreadyProcessed(staff, task, DeliveryTaskActionEnum.DELIVER, request.getRequestId())) {
			return true;
		}
		DeliveryTaskStatusEnum from = statusOf(task);
		requireTransition(from, DeliveryTaskStatusEnum.DELIVERED);
		String reservationId = reservationId(taskId, request.getRequestId());
		materialAccessService.reserveForDelivery(staff.getTenantId(), staff.getUserId(), request.getMaterialIds(),
			reservationId);
		LocalDateTime now = LocalDateTime.now();
		requireUpdated(taskMapper.updateStaffStatus(staff.getTenantId(), staff.getUserId(), taskId, from.name(),
			DeliveryTaskStatusEnum.DELIVERED.name(), request.getVersion(), now, "deliveredAt"));
		saveEvidence(task, staff, request.getMaterialIds(), DeliveryEvidenceTypeEnum.DELIVERED, now);
		appendLog(task, DeliveryTaskActionEnum.DELIVER, from, DeliveryTaskStatusEnum.DELIVERED,
			staff, request.getRequestId(), null, now);
		confirmBindingAfterCommit(staff.getTenantId(), reservationId, taskId);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean reportException(String taskId, DeliveryExceptionRequest request) {
		ArynUser staff = currentStaff();
		validateRequest(request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		if (!StringUtils.hasText(request.getReasonCode()) || !StringUtils.hasText(request.getDescription())) {
			throw new ArynBusinessException("配送异常原因和说明不能为空");
		}
		validateMaterialIds(request.getMaterialIds(), false);
		OrderDeliveryTask task = requireTask(staff, taskId);
		if (alreadyProcessed(staff, task, DeliveryTaskActionEnum.REPORT_EXCEPTION, request.getRequestId())) {
			return true;
		}
		DeliveryTaskStatusEnum from = statusOf(task);
		if (!EnumSet.of(DeliveryTaskStatusEnum.ASSIGNED, DeliveryTaskStatusEnum.PICKING,
				DeliveryTaskStatusEnum.DELIVERING).contains(from)
				|| !transitionPolicy.canTransit(from, DeliveryTaskStatusEnum.EXCEPTION)) {
			throw new ArynBusinessException("当前配送任务状态不允许上报异常");
		}
		String reservationId = null;
		if (!CollectionUtils.isEmpty(request.getMaterialIds())) {
			reservationId = reservationId(taskId, request.getRequestId());
			materialAccessService.reserveForDelivery(staff.getTenantId(), staff.getUserId(), request.getMaterialIds(),
				reservationId);
		}
		LocalDateTime now = LocalDateTime.now();
		requireUpdated(taskMapper.reportStaffException(staff.getTenantId(), staff.getUserId(), taskId, from.name(),
			request.getVersion(), request.getReasonCode(), request.getDescription(), now));
		saveEvidence(task, staff, request.getMaterialIds(), DeliveryEvidenceTypeEnum.EXCEPTION, now);
		appendLog(task, DeliveryTaskActionEnum.REPORT_EXCEPTION, from, DeliveryTaskStatusEnum.EXCEPTION,
			staff, request.getRequestId(), request.getDescription(), now);
		if (reservationId != null) {
			confirmBindingAfterCommit(staff.getTenantId(), reservationId, taskId);
		}
		return true;
	}

	private ArynUser currentStaff() {
		return SecurityUtils.requireUser(DeviceTypeEnum.TOB);
	}

	private OrderDeliveryTask requireTask(ArynUser staff, String taskId) {
		OrderDeliveryTask task = taskMapper.selectByTenantAssigneeAndId(staff.getTenantId(), staff.getUserId(), taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在或已改派");
		}
		return task;
	}

	private boolean alreadyProcessed(ArynUser staff, OrderDeliveryTask task, DeliveryTaskActionEnum action,
			String requestId) {
		return logMapper.selectByRequest(staff.getTenantId(), task.getId(), action.name(), requestId) != null;
	}

	private void validateRequest(Integer version, String requestId) {
		if (version == null || version < 0 || !StringUtils.hasText(requestId)) {
			throw new ArynBusinessException("配送任务版本或请求幂等号无效");
		}
	}

	private void validateMaterialIds(List<String> materialIds, boolean required) {
		List<String> ids = materialIds == null ? List.of() : materialIds;
		if ((required && ids.isEmpty()) || ids.size() > 6
				|| ids.stream().anyMatch(id -> !StringUtils.hasText(id))
				|| new HashSet<>(ids).size() != ids.size()) {
			throw new ArynBusinessException("配送凭证必须为 1 至 6 个不重复的有效素材");
		}
	}

	private DeliveryTaskStatusEnum statusOf(OrderDeliveryTask task) {
		try {
			return DeliveryTaskStatusEnum.valueOf(task.getStatus());
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("配送任务状态无效");
		}
	}

	private void requireTransition(DeliveryTaskStatusEnum from, DeliveryTaskStatusEnum to) {
		if (!transitionPolicy.canTransit(from, to)) {
			throw new ArynBusinessException("当前配送任务状态不允许执行该操作");
		}
	}

	private void requireUpdated(int updated) {
		if (updated != 1) {
			throw new ArynBusinessException("配送任务已变化或已改派，请刷新后重试");
		}
	}

	private void saveEvidence(OrderDeliveryTask task, ArynUser staff, List<String> materialIds,
			DeliveryEvidenceTypeEnum evidenceType, LocalDateTime now) {
		if (CollectionUtils.isEmpty(materialIds)) {
			return;
		}
		for (int index = 0; index < materialIds.size(); index++) {
			OrderDeliveryEvidence evidence = new OrderDeliveryEvidence()
				.setId(IdWorker.getIdStr())
				.setTaskId(task.getId())
				.setAttemptNo(task.getAttemptNo())
				.setEvidenceType(evidenceType.name())
				.setMaterialId(materialIds.get(index))
				.setBindingStatus("PENDING")
				.setSortNo(index)
				.setUploadedBy(staff.getUserId())
				.setTenantId(staff.getTenantId())
				.setCreateBy(staff.getUserId())
				.setCreateTime(now)
				.setDelFlag(CommonConstants.NO);
			if (evidenceMapper.insert(evidence) != 1) {
				throw new ArynBusinessException("配送凭证保存失败，请重试");
			}
		}
	}

	private String reservationId(String taskId, String requestId) {
		return taskId + ":" + requestId;
	}

	private void confirmBindingAfterCommit(String tenantId, String reservationId, String taskId) {
		TransactionalMqUtils.sendAfterCommit(() -> {
			try {
				bindingService.confirmBinding(tenantId, reservationId, taskId);
			}
			catch (RuntimeException exception) {
				log.error("配送凭证提交后绑定确认失败，将由恢复任务重试，tenantId={}, taskId={}, reservationId={}",
					tenantId, taskId, reservationId, exception);
			}
		});
	}

	private void appendLog(OrderDeliveryTask task, DeliveryTaskActionEnum action, DeliveryTaskStatusEnum from,
			DeliveryTaskStatusEnum to, ArynUser staff, String requestId, String description, LocalDateTime now) {
		OrderDeliveryTaskLog log = new OrderDeliveryTaskLog()
			.setId(IdWorker.getIdStr())
			.setTaskId(task.getId())
			.setAction(action.name())
			.setFromStatus(from.name())
			.setToStatus(to.name())
			.setAttemptNo(task.getAttemptNo())
			.setOperatorType("STAFF")
			.setOperatorId(staff.getUserId())
			.setOperatorName(staff.getNickname())
			.setDescription(description)
			.setRequestId(requestId)
			.setTenantId(staff.getTenantId())
			.setCreateBy(staff.getUserId())
			.setCreateTime(now)
			.setDelFlag(CommonConstants.NO);
		if (logMapper.insert(log) != 1) {
			throw new ArynBusinessException("配送任务日志写入失败，请重试");
		}
	}

	private DeliveryTaskStaffVO toView(OrderDeliveryTask task, boolean detail) {
		DeliveryTaskStaffVO view = new DeliveryTaskStaffVO();
		BeanUtils.copyProperties(task, view);
		if (!detail) {
			return view;
		}
		OrderInfo order = orderInfoMapper.selectById(task.getOrderId());
		if (order != null) {
			view.setRecipientName(order.getRecipientName());
			view.setRecipientPhone(order.getRecipientPhone());
			view.setRecipientProvince(order.getRecipientProvince());
			view.setRecipientCity(order.getRecipientCity());
			view.setRecipientArea(order.getRecipientArea());
			view.setRecipientAddress(order.getRecipientAddress());
		}
		view.setItems(taskItemMapper.selectList(Wrappers.<OrderDeliveryTaskItem>lambdaQuery()
			.eq(OrderDeliveryTaskItem::getTenantId, task.getTenantId())
			.eq(OrderDeliveryTaskItem::getTaskId, task.getId())
			.eq(OrderDeliveryTaskItem::getAttemptNo, task.getAttemptNo())
			.orderByAsc(OrderDeliveryTaskItem::getCreateTime)));
		view.setEvidence(new ArrayList<>(evidenceMapper.selectList(Wrappers.<OrderDeliveryEvidence>lambdaQuery()
			.eq(OrderDeliveryEvidence::getTenantId, task.getTenantId())
			.eq(OrderDeliveryEvidence::getTaskId, task.getId())
			.eq(OrderDeliveryEvidence::getAttemptNo, task.getAttemptNo())
			.orderByAsc(OrderDeliveryEvidence::getSortNo))));
		return view;
	}

}
