package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderRefund;

/**
 * 订单业务事件向租户工作人员（ROLE_ADMIN）发送站内通知。
 *
 * <p>新订单、取消订单、退款申请这类「需要人来处理」的事实必须让工作人员知道，
 * 否则管理端只能靠人工刷新列表发现。收件人解析与货到付款收款预警同构
 * （按角色码查租户下有效员工），发送走消息域 MQ 命令。
 *
 * <p>本服务不抛异常：收件人查询与发送失败只记日志，不影响业务事实。
 * 业务事务内调用时由调用方负责「提交后才发送」（参见实现类注释）。
 *
 * @author Aetheryn
 * @date 2026/10/16
 */
public interface OrderStaffNotifyService {

	/**
	 * 新订单提交：通知租户管理员有待处理订单。
	 * @param order 订单实体（取订单号、金额、收货人、支付方式）
	 */
	void notifyOrderCreated(OrderInfo order);

	/**
	 * 订单取消：按来源生成不同标题，让工作人员区分买家取消、超时取消与管理端取消。
	 * @param order 订单实体
	 * @param source 取消来源
	 */
	void notifyOrderCanceled(OrderInfo order, CancelSource source);

	/**
	 * 退款申请：买家提交售后申请，等待工作人员审核（订单号由本服务回查）。
	 * @param refund 退款单实体（取退款单号、金额、申请原因、关联订单）
	 */
	void notifyRefundApplied(OrderRefund refund);

	/** 订单取消来源，决定通知标题。 */
	enum CancelSource {

		/** 买家主动取消 */
		BUYER("买家取消订单"),
		/** 超时未支付自动取消 */
		TIMEOUT("订单超时自动取消"),
		/** 管理端取消 */
		ADMIN("管理端取消订单"),
		/** 其他来源（兜底） */
		OTHER("订单已取消");

		private final String title;

		CancelSource(String title) {
			this.title = title;
		}

		public String getTitle() {
			return title;
		}

	}

}
