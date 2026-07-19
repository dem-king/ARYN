
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

}
