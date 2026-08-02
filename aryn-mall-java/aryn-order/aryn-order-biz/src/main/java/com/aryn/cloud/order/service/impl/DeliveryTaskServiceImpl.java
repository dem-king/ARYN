
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.dto.DeliveryAssignDTO;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.DeliveryTripStatusEnum;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.service.*;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

	private final RocketMQTemplate rocketMQTemplate;

	private static final DateTimeFormatter TRIP_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean createTaskOnPay(OrderInfo orderInfo, List<OrderItemEntity> orderItems) {
		if (orderInfo == null || !MallOrderConstants.DELIVERY_WAY_3.equals(orderInfo.getDeliveryWay())) {
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
			item.setTenantId(orderInfo.getTenantId());
			deliveryTaskItemService.save(item);
		}
		saveLog(task.getId(), "CREATE", null, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(), 1,
				"3", null, "支付成功自动创建任务");
		log.info("订单[{}]支付成功，自动创建配送任务[{}]", orderInfo.getId(), task.getId());
		return Boolean.TRUE;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String assignTasks(DeliveryAssignDTO dto) {
		List<String> taskIds = dto.getTaskIds();
		if (CollUtil.isEmpty(taskIds)) {
			throw new ArynBusinessException("任务ID列表不能为空");
		}
		DeliveryStaff staff = deliveryStaffService.getById(dto.getStaffId());
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		List<DeliveryTask> tasks = list(Wrappers.<DeliveryTask>lambdaQuery()
			.in(DeliveryTask::getId, taskIds)
			.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode()));
		if (tasks.size() != taskIds.size()) {
			throw new ArynBusinessException("存在非待派单状态的任务，无法派单");
		}
		DeliveryWarehouseConfig warehouseConfig = deliveryWarehouseConfigService.getConfig();
		String warehouseAddress = warehouseConfig == null ? null : buildWarehouseAddress(warehouseConfig);

		DeliveryTrip trip = new DeliveryTrip();
		trip.setTripNo(generateTripNo());
		trip.setStaffId(staff.getId());
		trip.setStatus(DeliveryTripStatusEnum.WAITING_LOAD.getCode());
		trip.setTaskCount(tasks.size());
		trip.setWarehouseAddress(warehouseAddress);
		trip.setTenantId(staff.getTenantId());
		deliveryTripService.save(trip);

		LocalDateTime now = LocalDateTime.now();
		for (int i = 0; i < tasks.size(); i++) {
			DeliveryTask task = tasks.get(i);
			int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
				.eq(DeliveryTask::getId, task.getId())
				.eq(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode())
				.set(DeliveryTask::getTripId, trip.getId())
				.set(DeliveryTask::getStaffId, staff.getId())
				.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_PICK.getCode())
				.set(DeliveryTask::getSortNo, i + 1)
				.set(DeliveryTask::getAssignTime, now)
				.set(DeliveryTask::getWarehouseAddress, warehouseAddress));
			if (updated == 0) {
				throw new ArynBusinessException("任务[" + task.getTaskNo() + "]状态已变化，派单失败");
			}
			saveLog(task.getId(), "ASSIGN", DeliveryTaskStatusEnum.WAITING_ASSIGN.getCode(),
					DeliveryTaskStatusEnum.WAITING_PICK.getCode(), task.getAttemptNo(),
					"1", staff.getId(), "派单给[" + staff.getStaffName() + "]");
		}
		log.info("批量派单成功，出车单[{}]，任务数[{}]", trip.getId(), tasks.size());
		sendAssignNotification(staff, tasks, trip);
		return trip.getId();
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
		if (staff == null) {
			throw new ArynBusinessException("配送员不存在");
		}
		int newAttemptNo = (task.getAttemptNo() == null ? 1 : task.getAttemptNo()) + 1;
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, taskId)
			.eq(DeliveryTask::getVersion, task.getVersion())
			.set(DeliveryTask::getStaffId, staffId)
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.WAITING_PICK.getCode())
			.set(DeliveryTask::getAttemptNo, newAttemptNo)
			.set(DeliveryTask::getAssignTime, LocalDateTime.now())
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
		saveLog(taskId, "REASSIGN", task.getStatus(), DeliveryTaskStatusEnum.WAITING_PICK.getCode(),
				newAttemptNo, "1", staffId, "改派给[" + staff.getStaffName() + "]");
		return Boolean.TRUE;
	}

	@Override
	public DeliveryTask getTaskDetail(String id) {
		DeliveryTask task = getById(id);
		if (task == null) {
			return null;
		}
		task.setItemList(deliveryTaskItemService.listByTaskId(id));
		return task;
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
		if (!DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode().equals(task.getStatus())) {
			throw new ArynBusinessException("当前任务状态不允许送达");
		}
		if (CollUtil.isNotEmpty(materialIds)) {
			if (materialIds.size() < 1 || materialIds.size() > 6) {
				throw new ArynBusinessException("送达凭证图片数量必须为1至6张");
			}
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
		if (CollUtil.isNotEmpty(materialIds)) {
			for (int i = 0; i < materialIds.size(); i++) {
				DeliveryEvidence evidence = new DeliveryEvidence();
				evidence.setTaskId(taskId);
				evidence.setAttemptNo(task.getAttemptNo());
				evidence.setEvidenceType("1");
				evidence.setMaterialId(materialIds.get(i));
				evidence.setSortNo(i + 1);
				evidence.setUploadBy(staffId);
				evidence.setTenantId(task.getTenantId());
				deliveryEvidenceService.save(evidence);
			}
		}
		saveLog(taskId, "ARRIVE", DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode(),
				DeliveryTaskStatusEnum.ARRIVED.getCode(), task.getAttemptNo(),
				"2", staffId, "送达确认");
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
		LocalDateTime now = LocalDateTime.now();
		int updated = baseMapper.update(null, Wrappers.<DeliveryTask>lambdaUpdate()
			.eq(DeliveryTask::getId, task.getId())
			.in(DeliveryTask::getStatus, DeliveryTaskStatusEnum.ARRIVED.getCode(),
					DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())
			.set(DeliveryTask::getStatus, DeliveryTaskStatusEnum.SIGNED.getCode())
			.set(DeliveryTask::getSignTime, now));
		if (updated == 0) {
			log.warn("订单[{}]签收联动失败，任务状态不匹配", orderId);
			return Boolean.FALSE;
		}
		if (StrUtil.isNotBlank(task.getTripId())) {
			long unsignedCount = count(Wrappers.<DeliveryTask>lambdaQuery()
				.eq(DeliveryTask::getTripId, task.getTripId())
				.ne(DeliveryTask::getStatus, DeliveryTaskStatusEnum.SIGNED.getCode())
				.ne(DeliveryTask::getStatus, DeliveryTaskStatusEnum.CANCELED.getCode()));
			if (unsignedCount == 0) {
				deliveryTripService.update(Wrappers.<DeliveryTrip>lambdaUpdate()
					.eq(DeliveryTrip::getId, task.getTripId())
					.eq(DeliveryTrip::getStatus, DeliveryTripStatusEnum.DELIVERING.getCode())
					.set(DeliveryTrip::getStatus, DeliveryTripStatusEnum.COMPLETED.getCode())
					.set(DeliveryTrip::getCompleteTime, now));
			}
		}
		saveLog(task.getId(), "SIGN", DeliveryTaskStatusEnum.ARRIVED.getCode(),
				DeliveryTaskStatusEnum.SIGNED.getCode(), task.getAttemptNo(),
				"3", null, "客户确认收货");
		return Boolean.TRUE;
	}

	@Override
	public DeliveryProgressVO getProgress(String orderId) {
		DeliveryTask task = getOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getOrderId, orderId));
		if (task == null) {
			return null;
		}
		DeliveryProgressVO vo = new DeliveryProgressVO();
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
		vo.setRecipientPhone(task.getRecipientPhone());
		vo.setRecipientAddress(task.getRecipientAddress());
		vo.setWarehouseAddress(task.getWarehouseAddress());
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
		return vo;
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
		}
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
			for (int i = 0; i < materialIds.size(); i++) {
				DeliveryEvidence evidence = new DeliveryEvidence();
				evidence.setTaskId(taskId);
				evidence.setAttemptNo(task.getAttemptNo());
				evidence.setEvidenceType("2");
				evidence.setMaterialId(materialIds.get(i));
				evidence.setSortNo(i + 1);
				evidence.setUploadBy(staffId);
				evidence.setTenantId(task.getTenantId());
				deliveryEvidenceService.save(evidence);
			}
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
		return Boolean.TRUE;
	}

	@Override
	public List<DeliveryEvidence> listEvidence(String taskId) {
		return deliveryEvidenceService.list(Wrappers.<DeliveryEvidence>lambdaQuery()
			.eq(DeliveryEvidence::getTaskId, taskId)
			.orderByAsc(DeliveryEvidence::getSortNo));
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
