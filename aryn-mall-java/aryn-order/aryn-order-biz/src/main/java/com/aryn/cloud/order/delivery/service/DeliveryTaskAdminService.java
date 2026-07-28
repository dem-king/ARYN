package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryAssignRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryCloseRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryReassignRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryReturnRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskLog;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskActionEnum;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskAdminVO;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskLogMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.upms.api.remote.RemoteDeliveryStaffService;
import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 管理端商城配送任务服务。 */
@Service
@RequiredArgsConstructor
public class DeliveryTaskAdminService {

	private static final Set<DeliveryTaskStatusEnum> REASSIGNABLE =
		EnumSet.of(DeliveryTaskStatusEnum.ASSIGNED, DeliveryTaskStatusEnum.PICKING,
			DeliveryTaskStatusEnum.EXCEPTION);

	private static final Set<DeliveryTaskStatusEnum> CLOSEABLE =
		EnumSet.of(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT, DeliveryTaskStatusEnum.ASSIGNED,
			DeliveryTaskStatusEnum.PICKING, DeliveryTaskStatusEnum.EXCEPTION);

	private final OrderDeliveryTaskMapper taskMapper;

	private final OrderDeliveryTaskItemMapper taskItemMapper;

	private final OrderDeliveryTaskLogMapper logMapper;

	private final DeliveryTaskTransitionPolicy transitionPolicy;

	@DubboReference
	private final RemoteDeliveryStaffService remoteDeliveryStaffService;

	public IPage<DeliveryTaskAdminVO> page(Page<OrderDeliveryTask> page, OrderDeliveryTask query) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		var wrapper = Wrappers.<OrderDeliveryTask>lambdaQuery()
			.eq(OrderDeliveryTask::getTenantId, operator.getTenantId());
		if (query != null) {
			wrapper.eq(StringUtils.hasText(query.getStatus()), OrderDeliveryTask::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getAssigneeId()), OrderDeliveryTask::getAssigneeId,
					query.getAssigneeId())
				.like(StringUtils.hasText(query.getTaskNo()), OrderDeliveryTask::getTaskNo, query.getTaskNo())
				.like(StringUtils.hasText(query.getOrderNo()), OrderDeliveryTask::getOrderNo, query.getOrderNo());
		}
		wrapper.orderByDesc(OrderDeliveryTask::getCreateTime);
		return taskMapper.selectPage(page, wrapper).convert(this::toView);
	}

	public DeliveryTaskAdminVO get(String taskId) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		OrderDeliveryTask task = requireTask(operator.getTenantId(), taskId);
		DeliveryTaskAdminVO view = toView(task);
		view.setLogs(logMapper.selectList(Wrappers.<OrderDeliveryTaskLog>lambdaQuery()
			.eq(OrderDeliveryTaskLog::getTenantId, operator.getTenantId())
			.eq(OrderDeliveryTaskLog::getTaskId, taskId)
			.orderByAsc(OrderDeliveryTaskLog::getCreateTime)));
		return view;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean assign(String taskId, DeliveryAssignRequest request) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		validateAssignmentRequest(request == null ? null : request.getAssigneeId(),
			request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		OrderDeliveryTask task = requireTask(operator.getTenantId(), taskId);
		DeliveryStaffVO staff = requireEligibleStaff(operator.getTenantId(), request.getAssigneeId());
		DeliveryTaskStatusEnum from = statusOf(task);
		if (from != DeliveryTaskStatusEnum.WAITING_ASSIGNMENT
				|| !transitionPolicy.canTransit(from, DeliveryTaskStatusEnum.ASSIGNED)) {
			throw new ArynBusinessException("当前配送任务状态不允许首次派单");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = taskMapper.assign(operator.getTenantId(), taskId, from.name(), request.getVersion(), staff,
			operator.getUserId(), operatorName(operator), now);
		requireUpdated(updated);
		appendLog(task, DeliveryTaskActionEnum.ASSIGN, from, DeliveryTaskStatusEnum.ASSIGNED,
			task.getAttemptNo(), operator, null, request.getRemark(), request.getRequestId(), now);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean reassign(String taskId, DeliveryReassignRequest request) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		validateAssignmentRequest(request == null ? null : request.getAssigneeId(),
			request == null ? null : request.getVersion(), request == null ? null : request.getRequestId());
		if (!StringUtils.hasText(request.getReasonCode())) {
			throw new ArynBusinessException("改派原因不能为空");
		}
		OrderDeliveryTask task = requireTask(operator.getTenantId(), taskId);
		DeliveryTaskStatusEnum from = statusOf(task);
		if (!REASSIGNABLE.contains(from)) {
			throw new ArynBusinessException("当前配送任务状态不允许改派");
		}
		DeliveryStaffVO staff = requireEligibleStaff(operator.getTenantId(), request.getAssigneeId());
		LocalDateTime now = LocalDateTime.now();
		int updated = taskMapper.reassign(operator.getTenantId(), taskId, from.name(), request.getVersion(), staff,
			operator.getUserId(), operatorName(operator), now);
		requireUpdated(updated);
		int nextAttempt = task.getAttemptNo() + 1;
		taskItemMapper.resetForAttempt(operator.getTenantId(), taskId, nextAttempt);
		appendLog(task, DeliveryTaskActionEnum.REASSIGN, from, DeliveryTaskStatusEnum.ASSIGNED, nextAttempt,
			operator, request.getReasonCode(), request.getDescription(), request.getRequestId(), now);
		return true;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean close(String taskId, DeliveryCloseRequest request) {
		validateStatusRequest(request == null ? null : request.getVersion(),
			request == null ? null : request.getRequestId());
		if (!StringUtils.hasText(request.getReasonCode())) {
			throw new ArynBusinessException("关闭原因不能为空");
		}
		return updateStatus(taskId, request.getVersion(), DeliveryTaskStatusEnum.CLOSED,
			DeliveryTaskActionEnum.CLOSE, request.getReasonCode(), request.getDescription(), request.getRequestId(),
			"closedAt", CLOSEABLE);
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean markReturnPending(String taskId, DeliveryReturnRequest request) {
		validateStatusRequest(request == null ? null : request.getVersion(),
			request == null ? null : request.getRequestId());
		return updateStatus(taskId, request.getVersion(), DeliveryTaskStatusEnum.RETURN_PENDING,
			DeliveryTaskActionEnum.MARK_RETURN_PENDING, request.getReasonCode(), request.getDescription(),
			request.getRequestId(), "returnPendingAt",
			EnumSet.of(DeliveryTaskStatusEnum.DELIVERING, DeliveryTaskStatusEnum.DELIVERED,
				DeliveryTaskStatusEnum.EXCEPTION));
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean confirmReturn(String taskId, DeliveryReturnRequest request) {
		validateStatusRequest(request == null ? null : request.getVersion(),
			request == null ? null : request.getRequestId());
		return updateStatus(taskId, request.getVersion(), DeliveryTaskStatusEnum.CLOSED,
			DeliveryTaskActionEnum.CONFIRM_RETURN, request.getReasonCode(), request.getDescription(),
			request.getRequestId(), "returnedAt", EnumSet.of(DeliveryTaskStatusEnum.RETURN_PENDING));
	}

	private boolean updateStatus(String taskId, int version, DeliveryTaskStatusEnum target,
			DeliveryTaskActionEnum action, String reasonCode, String description, String requestId,
			String timeField, Set<DeliveryTaskStatusEnum> allowedSources) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		OrderDeliveryTask task = requireTask(operator.getTenantId(), taskId);
		DeliveryTaskStatusEnum from = statusOf(task);
		if (!allowedSources.contains(from) || !transitionPolicy.canTransit(from, target)) {
			throw new ArynBusinessException("当前配送任务状态不允许执行该操作");
		}
		LocalDateTime now = LocalDateTime.now();
		int updated = taskMapper.updateAdminStatus(operator.getTenantId(), taskId, from.name(), version,
			target.name(), reasonCode, description, now, timeField, operator.getUserId());
		requireUpdated(updated);
		appendLog(task, action, from, target, task.getAttemptNo(), operator, reasonCode, description, requestId, now);
		return true;
	}

	private OrderDeliveryTask requireTask(String tenantId, String taskId) {
		if (!StringUtils.hasText(taskId)) {
			throw new ArynBusinessException("配送任务ID不能为空");
		}
		OrderDeliveryTask task = taskMapper.selectByTenantAndId(tenantId, taskId);
		if (task == null) {
			throw new ArynBusinessException("配送任务不存在");
		}
		return task;
	}

	private DeliveryStaffVO requireEligibleStaff(String tenantId, String staffId) {
		DeliveryStaffVO staff = remoteDeliveryStaffService.getEligibleStaff(tenantId, staffId);
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在、已停用或无配送权限");
		}
		return staff;
	}

	private DeliveryTaskStatusEnum statusOf(OrderDeliveryTask task) {
		try {
			return DeliveryTaskStatusEnum.valueOf(task.getStatus());
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("配送任务状态无效");
		}
	}

	private void validateAssignmentRequest(String assigneeId, Integer version, String requestId) {
		if (!StringUtils.hasText(assigneeId)) {
			throw new ArynBusinessException("配送员ID不能为空");
		}
		validateStatusRequest(version, requestId);
	}

	private void validateStatusRequest(Integer version, String requestId) {
		if (version == null || version < 0) {
			throw new ArynBusinessException("配送任务版本不能为空");
		}
		if (!StringUtils.hasText(requestId)) {
			throw new ArynBusinessException("请求幂等号不能为空");
		}
	}

	private void requireUpdated(int updated) {
		if (updated != 1) {
			throw new ArynBusinessException("配送任务已变化，请刷新后重试");
		}
	}

	private void appendLog(OrderDeliveryTask task, DeliveryTaskActionEnum action, DeliveryTaskStatusEnum from,
			DeliveryTaskStatusEnum to, int attemptNo, ArynUser operator, String reasonCode, String description,
			String requestId, LocalDateTime now) {
		OrderDeliveryTaskLog log = new OrderDeliveryTaskLog()
			.setId(IdWorker.getIdStr())
			.setTaskId(task.getId())
			.setAction(action.name())
			.setFromStatus(from.name())
			.setToStatus(to.name())
			.setAttemptNo(attemptNo)
			.setOperatorType("ADMIN")
			.setOperatorId(operator.getUserId())
			.setOperatorName(operatorName(operator))
			.setReasonCode(reasonCode)
			.setDescription(description)
			.setRequestId(requestId)
			.setTenantId(operator.getTenantId())
			.setCreateBy(operator.getUserId())
			.setCreateTime(now)
			.setDelFlag(CommonConstants.NO);
		if (logMapper.insert(log) != 1) {
			throw new ArynBusinessException("配送任务日志写入失败，请重试");
		}
	}

	private String operatorName(ArynUser operator) {
		return StringUtils.hasText(operator.getNickname()) ? operator.getNickname() : operator.getUsername();
	}

	private DeliveryTaskAdminVO toView(OrderDeliveryTask task) {
		DeliveryTaskAdminVO view = new DeliveryTaskAdminVO();
		BeanUtils.copyProperties(task, view);
		return view;
	}

}
