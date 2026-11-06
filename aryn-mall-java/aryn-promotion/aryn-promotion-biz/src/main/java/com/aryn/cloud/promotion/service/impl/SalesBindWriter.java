package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBindLog;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
import com.aryn.cloud.promotion.api.enums.SalesCustomerClaimStatusEnum;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindLogMapper;
import com.aryn.cloud.promotion.mapper.SalesCustomerBindMapper;
import com.aryn.cloud.promotion.mapper.SalesCustomerClaimMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 客户归属写操作（换绑 + 日志 + 认领失效）
 * <p>
 * 只依赖三个 Mapper，供 {@link SalesStaffServiceImpl}、{@link SalesCustomerBindServiceImpl}、
 * {@link SalesSettlementServiceImpl} 复用，避免 Service 之间循环依赖。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesBindWriter {

	private final SalesCustomerBindMapper salesCustomerBindMapper;

	private final SalesCustomerBindLogMapper salesCustomerBindLogMapper;

	private final SalesCustomerClaimMapper salesCustomerClaimMapper;

	/**
	 * 写入/更新客户归属并追加换绑日志。
	 * @param customerUserId 客户会员ID
	 * @param newStaff 新销售（取其 ID/姓名）
	 * @param bindSource 绑定来源
	 * @param oldStaff 原销售（可空）
	 * @param oldSalesStaffName 原归属展示名（原销售为空时用于记录原分销员等关系）
	 * @param reason 变更原因
	 * @return 归属记录
	 */
	@Transactional(rollbackFor = Exception.class)
	public SalesCustomerBind upsertBind(String customerUserId, SalesStaff newStaff, String bindSource,
			SalesStaff oldStaff, String oldSalesStaffName, String reason) {
		LocalDateTime now = LocalDateTime.now();
		SalesCustomerBind bind = salesCustomerBindMapper.selectOne(Wrappers.<SalesCustomerBind>lambdaQuery()
			.eq(SalesCustomerBind::getCustomerUserId, customerUserId)
			.last("limit 1"));
		String oldStaffId = null;
		String oldStaffName = oldSalesStaffName;
		if (oldStaff != null) {
			oldStaffId = oldStaff.getId();
			oldStaffName = oldStaff.getStaffName();
		}
		if (bind == null) {
			bind = new SalesCustomerBind().setCustomerUserId(customerUserId)
				.setSalesStaffId(newStaff.getId())
				.setBindSource(bindSource)
				.setBindTime(now);
			if (salesCustomerBindMapper.insert(bind) <= 0) {
				throw new ArynBusinessException("客户归属保存失败");
			}
		}
		else {
			bind.setSalesStaffId(newStaff.getId());
			bind.setBindSource(bindSource);
			bind.setBindTime(now);
			if (salesCustomerBindMapper.updateById(bind) <= 0) {
				throw new ArynBusinessException("客户归属更新失败");
			}
		}
		writeLog(customerUserId, oldStaffId, oldStaffName, newStaff.getId(), newStaff.getStaffName(), bindSource, reason);
		return bind;
	}

	/**
	 * 离职交接：把一条归属记录改到接收人（来源 TRANSFER）并写日志。
	 * @param bind 原归属
	 * @param oldStaff 离职销售
	 * @param receiveStaff 接收销售
	 * @param reason 交接原因
	 */
	@Transactional(rollbackFor = Exception.class)
	public void transferBind(SalesCustomerBind bind, SalesStaff oldStaff, SalesStaff receiveStaff, String reason) {
		bind.setSalesStaffId(receiveStaff.getId());
		bind.setBindSource("TRANSFER");
		bind.setBindTime(LocalDateTime.now());
		if (salesCustomerBindMapper.updateById(bind) <= 0) {
			throw new ArynBusinessException("客户归属交接失败");
		}
		writeLog(bind.getCustomerUserId(), oldStaff.getId(), oldStaff.getStaffName(), receiveStaff.getId(),
				receiveStaff.getStaffName(), "TRANSFER", reason);
	}

	/**
	 * 作废客户未完成认领。
	 * @param customerUserId 客户会员ID
	 * @return 作废条数
	 */
	@Transactional(rollbackFor = Exception.class)
	public int invalidatePendingClaims(String customerUserId) {
		SalesCustomerClaim update = new SalesCustomerClaim();
		update.setStatus(SalesCustomerClaimStatusEnum.STATUS_2.getCode());
		return salesCustomerClaimMapper.update(update, Wrappers.<SalesCustomerClaim>lambdaUpdate()
			.eq(SalesCustomerClaim::getCustomerUserId, customerUserId)
			.eq(SalesCustomerClaim::getStatus, SalesCustomerClaimStatusEnum.STATUS_0.getCode()));
	}

	/**
	 * 作废销售名下未完成认领（离职交接）。
	 * @param salesStaffId 销售ID
	 * @return 作废条数
	 */
	@Transactional(rollbackFor = Exception.class)
	public int invalidatePendingClaimsByStaff(String salesStaffId) {
		SalesCustomerClaim update = new SalesCustomerClaim();
		update.setStatus(SalesCustomerClaimStatusEnum.STATUS_2.getCode());
		return salesCustomerClaimMapper.update(update, Wrappers.<SalesCustomerClaim>lambdaUpdate()
			.eq(SalesCustomerClaim::getSalesStaffId, salesStaffId)
			.eq(SalesCustomerClaim::getStatus, SalesCustomerClaimStatusEnum.STATUS_0.getCode()));
	}

	/**
	 * 认领置为已转交。
	 * @param claimId 认领ID
	 * @return 是否成功
	 */
	@Transactional(rollbackFor = Exception.class)
	public boolean markClaimTransferred(String claimId) {
		SalesCustomerClaim claim = salesCustomerClaimMapper.selectById(claimId);
		if (claim == null || !SalesCustomerClaimStatusEnum.STATUS_0.getCode().equals(claim.getStatus())) {
			return false;
		}
		claim.setStatus(SalesCustomerClaimStatusEnum.STATUS_1.getCode());
		return salesCustomerClaimMapper.updateById(claim) > 0;
	}

	private void writeLog(String customerUserId, String oldStaffId, String oldStaffName, String newStaffId,
			String newStaffName, String bindSource, String reason) {
		SalesCustomerBindLog bindLog = new SalesCustomerBindLog();
		bindLog.setCustomerUserId(customerUserId);
		bindLog.setOldSalesStaffId(oldStaffId);
		bindLog.setOldSalesStaffName(oldStaffName);
		bindLog.setNewSalesStaffId(newStaffId);
		bindLog.setNewSalesStaffName(newStaffName);
		bindLog.setBindSource(bindSource);
		bindLog.setReason(reason);
		ArynUser operator = currentOperator();
		if (operator != null) {
			bindLog.setOperatorId(operator.getUserId());
			bindLog.setOperatorName(operator.getUsername());
		}
		if (salesCustomerBindLogMapper.insert(bindLog) <= 0) {
			throw new ArynBusinessException("客户换绑日志保存失败");
		}
	}

	/**
	 * 取当前操作人；定时任务/消息消费等无登录态场景返回 null，不阻断主流程。
	 */
	private ArynUser currentOperator() {
		try {
			return SecurityUtils.getUser();
		}
		catch (Exception e) {
			log.debug("读取当前操作人失败，按系统操作记录: {}", e.getMessage());
			return null;
		}
	}

}
