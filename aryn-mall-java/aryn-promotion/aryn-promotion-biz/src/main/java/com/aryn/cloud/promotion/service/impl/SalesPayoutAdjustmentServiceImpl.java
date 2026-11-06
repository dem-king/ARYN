package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import com.aryn.cloud.promotion.api.enums.SalesPayoutAdjustmentStatusEnum;
import com.aryn.cloud.promotion.mapper.SalesPayoutAdjustmentMapper;
import com.aryn.cloud.promotion.service.ISalesPayoutAdjustmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提成负数结转服务实现
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesPayoutAdjustmentServiceImpl extends ServiceImpl<SalesPayoutAdjustmentMapper, SalesPayoutAdjustment>
	implements ISalesPayoutAdjustmentService {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean recordAdjustment(String salesStaffId, String staffName, String bizOrderId, String refundNo,
			BigDecimal amount) {
		if (salesStaffId == null || refundNo == null || refundNo.isBlank()) {
			return false;
		}
		Long exists = this.baseMapper.selectCount(Wrappers.<SalesPayoutAdjustment>lambdaQuery()
			.eq(SalesPayoutAdjustment::getRefundNo, refundNo));
		if (exists != null && exists > 0) {
			log.info("已发放业绩单的退款调整已存在，跳过 refundNo={}", refundNo);
			return false;
		}
		SalesPayoutAdjustment adjustment = new SalesPayoutAdjustment();
		adjustment.setSalesStaffId(salesStaffId);
		adjustment.setStaffName(staffName);
		adjustment.setBizOrderId(bizOrderId);
		adjustment.setRefundNo(refundNo);
		adjustment.setAmount(amount);
		adjustment.setStatus(SalesPayoutAdjustmentStatusEnum.STATUS_0.getCode());
		adjustment.setRemark("已发放业绩单退款冲减");
		try {
			if (!this.save(adjustment)) {
				throw new ArynBusinessException("提成负数结转保存失败");
			}
		}
		catch (DuplicateKeyException e) {
			log.info("并发写入提成负数结转，跳过 refundNo={}", refundNo);
			return false;
		}
		return true;
	}

	@Override
	public List<SalesPayoutAdjustment> listPendingByStaff(String salesStaffId, LocalDateTime createTimeBefore) {
		return this.list(Wrappers.<SalesPayoutAdjustment>lambdaQuery()
			.eq(SalesPayoutAdjustment::getSalesStaffId, salesStaffId)
			.eq(SalesPayoutAdjustment::getStatus, SalesPayoutAdjustmentStatusEnum.STATUS_0.getCode())
			.lt(SalesPayoutAdjustment::getCreateTime, createTimeBefore)
			.orderByAsc(SalesPayoutAdjustment::getCreateTime));
	}

	@Override
	public List<SalesPayoutAdjustment> listPending(LocalDateTime createTimeBefore) {
		return this.list(Wrappers.<SalesPayoutAdjustment>lambdaQuery()
			.eq(SalesPayoutAdjustment::getStatus, SalesPayoutAdjustmentStatusEnum.STATUS_0.getCode())
			.lt(SalesPayoutAdjustment::getCreateTime, createTimeBefore)
			.orderByAsc(SalesPayoutAdjustment::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean settleAdjustments(List<SalesPayoutAdjustment> adjustments, String payoutId) {
		if (adjustments == null || adjustments.isEmpty()) {
			return true;
		}
		for (SalesPayoutAdjustment adjustment : adjustments) {
			adjustment.setStatus(SalesPayoutAdjustmentStatusEnum.STATUS_1.getCode());
			adjustment.setPayoutId(payoutId);
			if (!this.updateById(adjustment)) {
				throw new ArynBusinessException("提成负数结转状态更新失败");
			}
		}
		return true;
	}

}
