package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;

import java.util.List;

/**
 * 销售业绩结算服务
 * <p>
 * 支付成功：待转交认领转交 + 转交奖励 + 业绩单生成（同一事务）；退款成功：按比例扣回。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesSettlementService {

	/**
	 * 支付成功后生成销售业绩（幂等：biz_order_id 唯一）
	 * <p>业绩单创建即 status=1 可发放（settle_at 仅作退款风险观察期标记，不再作为放行门槛）。
	 * @param event 支付成功事件（salesStaffId 为空/销售离职/自购/基数为0 时跳过）
	 * @return true=本次生成业绩单，false=跳过或已存在
	 */
	Boolean handlePaySuccess(OrderPaySuccessEvent event);

	/**
	 * 退款成功后的提成扣回（按 refundNo 幂等）
	 * @param event 退款成功事件
	 * @return true=本次完成扣回，false=跳过/重复/等待支付事件
	 */
	Boolean handleRefund(OrderRefundSuccessEvent event);

	/**
	 * 当前租户尚未应用的退款记录 ID
	 * @return 退款记录 ID
	 */
	List<String> listPendingRefundIds();

	/**
	 * 重放单条未应用的退款记录（供定时任务使用）
	 * @param refundRecordId 退款记录ID
	 * @return true=本次应用成功
	 */
	Boolean replayPendingRefund(String refundRecordId);

	/**
	 * 把订单上尚未应用的退款补应用到业绩单（退款早于支付事件的场景）
	 * @param bizOrderId 业务订单ID
	 */
	void applyRecordedRefunds(String bizOrderId);

}
