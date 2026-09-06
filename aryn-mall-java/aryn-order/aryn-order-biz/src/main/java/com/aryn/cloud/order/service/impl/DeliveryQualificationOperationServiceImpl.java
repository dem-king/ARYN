
package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.order.api.entity.DeliveryQualificationOperation;
import com.aryn.cloud.order.mapper.DeliveryQualificationOperationMapper;
import com.aryn.cloud.order.service.IDeliveryQualificationOperationService;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 配送资格操作（可靠授权/回收记录）
 *
 * <p>执行采用"条件认领"：仅当记录仍为待处理且到达重试时间时推进重试时间，
 * 多实例并发下同一记录只会被一方执行；远程角色变更本身幂等（存在即成功），
 * 重复执行不会产生重复角色关系。
 *
 * @author aryn
 * @since 2026/9/6
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryQualificationOperationServiceImpl
		extends ServiceImpl<DeliveryQualificationOperationMapper, DeliveryQualificationOperation>
		implements IDeliveryQualificationOperationService {

	/** 首次重试退避（分钟） */
	private static final int BASE_BACKOFF_MINUTES = 1;

	/** 重试退避上限（分钟） */
	private static final int MAX_BACKOFF_MINUTES = 60;

	/** last_error 字段截断长度 */
	private static final int MAX_LAST_ERROR_LENGTH = 500;

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	@Override
	public DeliveryQualificationOperation createPending(String staffId, String sysUserId, String roleCode,
			String operation, String operator) {
		String idempotentKey = StrUtil.format("{}:{}:{}", operation, sysUserId, roleCode);
		DeliveryQualificationOperation existing = getOne(Wrappers.<DeliveryQualificationOperation>lambdaQuery()
			.eq(DeliveryQualificationOperation::getIdempotentKey, idempotentKey), false);
		if (existing != null) {
			// 复用历史记录重置为待处理，避免重复行
			update(Wrappers.<DeliveryQualificationOperation>lambdaUpdate()
				.eq(DeliveryQualificationOperation::getId, existing.getId())
				.set(DeliveryQualificationOperation::getStatus, DeliveryQualificationOperation.STATUS_PENDING)
				.set(DeliveryQualificationOperation::getStaffId, staffId)
				.set(DeliveryQualificationOperation::getRetryCount, 0)
				.set(DeliveryQualificationOperation::getLastError, null)
				.set(DeliveryQualificationOperation::getNextRetryTime, null));
			existing.setStatus(DeliveryQualificationOperation.STATUS_PENDING);
			existing.setRetryCount(0);
			existing.setLastError(null);
			existing.setNextRetryTime(null);
			log.info("复用配送资格操作记录：operationId={}，operation={}，sysUserId={}", existing.getId(), operation,
					sysUserId);
			return existing;
		}
		DeliveryQualificationOperation op = new DeliveryQualificationOperation();
		op.setStaffId(staffId);
		op.setSysUserId(sysUserId);
		op.setRoleCode(roleCode);
		op.setOperation(operation);
		op.setStatus(DeliveryQualificationOperation.STATUS_PENDING);
		op.setIdempotentKey(idempotentKey);
		op.setRetryCount(0);
		save(op);
		log.info("创建配送资格操作：operationId={}，operation={}，sysUserId={}，操作人={}", op.getId(), operation, sysUserId,
				operator);
		return op;
	}

	@Override
	public boolean processPending(String operationId) {
		DeliveryQualificationOperation op = getById(operationId);
		if (op == null || DeliveryQualificationOperation.STATUS_DONE.equals(op.getStatus())) {
			return true;
		}
		// 条件认领：多实例并发下只有一方推进重试时间
		LocalDateTime now = LocalDateTime.now();
		int attempt = (op.getRetryCount() == null ? 0 : op.getRetryCount()) + 1;
		boolean claimed = update(Wrappers.<DeliveryQualificationOperation>lambdaUpdate()
			.eq(DeliveryQualificationOperation::getId, operationId)
			.eq(DeliveryQualificationOperation::getStatus, DeliveryQualificationOperation.STATUS_PENDING)
			.and(wrapper -> wrapper.isNull(DeliveryQualificationOperation::getNextRetryTime)
				.or().le(DeliveryQualificationOperation::getNextRetryTime, now))
			.set(DeliveryQualificationOperation::getRetryCount, attempt)
			.set(DeliveryQualificationOperation::getNextRetryTime, now.plusMinutes(nextBackoffMinutes(attempt))));
		if (!claimed) {
			return DeliveryQualificationOperation.STATUS_DONE.equals(getById(operationId).getStatus());
		}
		boolean grant = DeliveryQualificationOperation.OPERATION_GRANT.equals(op.getOperation());
		try {
			remoteSysUserService.changeRoleByCode(op.getSysUserId(), op.getRoleCode(), grant);
		}
		catch (Exception e) {
			recordError(operationId, e);
			log.error("配送资格操作执行失败，已转入重试：operationId={}，operation={}，sysUserId={}，角色={}，第{}次执行",
					operationId, op.getOperation(), op.getSysUserId(), op.getRoleCode(), attempt, e);
			return false;
		}
		markDone(operationId);
		log.info("配送资格操作执行成功：operationId={}，operation={}，sysUserId={}，角色={}，第{}次执行", operationId,
				op.getOperation(), op.getSysUserId(), op.getRoleCode(), attempt);
		return true;
	}

	@Override
	public List<DeliveryQualificationOperation> listDueForRetry(int limit) {
		return list(Wrappers.<DeliveryQualificationOperation>lambdaQuery()
			.eq(DeliveryQualificationOperation::getStatus, DeliveryQualificationOperation.STATUS_PENDING)
			.and(wrapper -> wrapper.isNull(DeliveryQualificationOperation::getNextRetryTime)
				.or().le(DeliveryQualificationOperation::getNextRetryTime, LocalDateTime.now()))
			.orderByAsc(DeliveryQualificationOperation::getCreateTime)
			.last("LIMIT " + Math.max(1, limit)));
	}

	/**
	 * 指数退避：1, 2, 4 ... 60 分钟封顶
	 */
	private int nextBackoffMinutes(int attempt) {
		return (int) Math.min(MAX_BACKOFF_MINUTES, (long) BASE_BACKOFF_MINUTES << Math.max(0, attempt - 1));
	}

	private void markDone(String operationId) {
		update(Wrappers.<DeliveryQualificationOperation>lambdaUpdate()
			.eq(DeliveryQualificationOperation::getId, operationId)
			.set(DeliveryQualificationOperation::getStatus, DeliveryQualificationOperation.STATUS_DONE)
			.set(DeliveryQualificationOperation::getLastError, null));
	}

	private void recordError(String operationId, Exception e) {
		String message = StrUtil.isBlank(e.getMessage()) ? e.getClass().getSimpleName() : e.getMessage();
		if (message.length() > MAX_LAST_ERROR_LENGTH) {
			message = message.substring(0, MAX_LAST_ERROR_LENGTH);
		}
		update(Wrappers.<DeliveryQualificationOperation>lambdaUpdate()
			.eq(DeliveryQualificationOperation::getId, operationId)
			.set(DeliveryQualificationOperation::getLastError, message));
	}

}
