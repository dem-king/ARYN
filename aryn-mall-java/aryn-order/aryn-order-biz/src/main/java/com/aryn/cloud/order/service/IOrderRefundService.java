
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

	/**
	 * 已取消订单的迟到支付补偿：记录支付事实并按订单项发起原路退款。
	 *
	 * <p>取消链路已释放库存、优惠券与积分，本方法只补退款，不重复回滚业务资源。
	 * 退款单号按「订单号 + 订单项」确定性生成，支付域按退款单号幂等；订单侧按明细跳过已有退款单。
	 *
	 * @param orderInfo 已标记支付成功的取消订单
	 * @param reason    退款原因
	 * @return true=已发起至少一笔退款
	 */
	boolean refundPaidCanceledOrder(com.aryn.cloud.order.api.entity.OrderInfo orderInfo, String reason);

}
