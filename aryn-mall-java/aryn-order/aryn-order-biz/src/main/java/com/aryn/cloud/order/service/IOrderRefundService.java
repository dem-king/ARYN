
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.OrderRefund;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
public interface IOrderRefundService extends IService<OrderRefund> {

	/**
	 * 商城退款单列表
	 * @param page
	 * @param orderRefund
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderRefund>
	 */
	IPage<OrderRefund> adminPage(Page page, OrderRefund orderRefund);

	/**
	 * 退款单详情
	 * @param id
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @return: com.aryn.cloud.mall.common.entity.OrderRefund
	 */
	OrderRefund getRefundById(String id);

	OrderRefund getUserRefundById(String id, String userId);

	/**
	 * 退款
	 * @param orderRefund
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @return: java.lang.Object
	 */
	boolean refund(OrderRefund orderRefund);

	/**
	 * 申请退款
	 * @param orderRefund
	 * @author 雨滴kian
	 * @date 2022/5/31
	 * @return: boolean
	 */
	OrderRefund saveRefund(OrderRefund orderRefund);

	/**
	 * 分页查询退单列表
	 * @param page
	 * @param orderRefund
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @return: com.baomidou.mybatisplus.core.metadata.IPage<com.aryn.cloud.mall.common.entity.OrderRefund>
	 */
	IPage<OrderRefund> getPage(Page page, OrderRefund orderRefund);

	/**
	 * 整单自动退款（系统侧发起，跳过售后审核）。
	 *
	 * <p>当前场景：拼团成团失败，对已付款成员整单退款。仅处理已支付、未取消的订单；
	 * 按订单项逐项创建退款单并发起支付退款，按订单项幂等（已有退款单的项跳过）。
	 *
	 * @param orderId 订单ID
	 * @param reason  退款原因（写入退款单）
	 * @return true=已发起至少一项退款；false=无需退款（未支付/已取消/均已退款）
	 */
	boolean refundWholeOrder(String orderId, String reason);

}
