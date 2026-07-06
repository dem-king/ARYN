package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.MemberPaidOrder;

/**
 * 付费会员订单
 *
 * @author aryn
 */
public interface IMemberPaidOrderService extends IService<MemberPaidOrder> {

	/**
	 * 付费会员订单分页查询
	 * @param page 分页参数
	 * @param memberPaidOrder 查询条件
	 * @return 分页结果
	 */
	IPage<MemberPaidOrder> getPage(Page page, MemberPaidOrder memberPaidOrder);

	/**
	 * 创建付费会员订单
	 * @param userId 用户ID
	 * @param memberLevelId 会员等级ID
	 * @return 付费会员订单
	 */
	MemberPaidOrder createOrder(String userId, String memberLevelId);

	/**
	 * 支付成功回调
	 * @param orderNo 订单号
	 */
	void paySuccess(String orderNo);

	/**
	 * 取消订单
	 * @param orderNo 订单号
	 */
	void cancelOrder(String orderNo);

	/**
	 * 检查付费会员到期，更新过期状态
	 */
	void checkExpiredOrders();

}