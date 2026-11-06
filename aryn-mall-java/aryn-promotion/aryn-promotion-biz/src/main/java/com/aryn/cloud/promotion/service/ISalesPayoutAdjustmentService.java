package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提成负数结转服务（已发放业绩单退款产生，结转到下月台账扣减）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesPayoutAdjustmentService extends IService<SalesPayoutAdjustment> {

	/**
	 * 记录一笔负数结转（按 refundNo 幂等）
	 * @param salesStaffId 销售ID
	 * @param staffName 销售姓名
	 * @param bizOrderId 业务订单ID
	 * @param refundNo 退款业务号
	 * @param amount 负数金额
	 * @return true=本次新增（重复退款返回 false）
	 */
	boolean recordAdjustment(String salesStaffId, String staffName, String bizOrderId, String refundNo, BigDecimal amount);

	/**
	 * 查询销售待结转的负数调整
	 * @param salesStaffId 销售ID
	 * @param createTimeBefore 调整创建时间上限（不含），台账月次月 1 日 0 点
	 * @return 待结转调整
	 */
	List<SalesPayoutAdjustment> listPendingByStaff(String salesStaffId, LocalDateTime createTimeBefore);

	/**
	 * 查询全部待结转的负数调整（台账生成时合并只有退款冲减、没有业绩单的销售）
	 * @param createTimeBefore 调整创建时间上限（不含），台账月次月 1 日 0 点，之后产生的调整留到下期
	 * @return 待结转调整
	 */
	List<SalesPayoutAdjustment> listPending(LocalDateTime createTimeBefore);

	/**
	 * 结转调整到批次（置为已结转并绑定批次）
	 * @param adjustments 调整列表
	 * @param payoutId 批次ID
	 * @return 是否成功
	 */
	boolean settleAdjustments(List<SalesPayoutAdjustment> adjustments, String payoutId);

}
