package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.RechargeOrder;

/**
 * 充值订单
 *
 * @author 雨滴kian
 */
public interface IRechargeOrderService extends IService<RechargeOrder> {

	IPage<RechargeOrder> getPage(Page page, RechargeOrder rechargeOrder);

	/**
	 * 创建充值订单
	 * @param userId 用户ID
	 * @param rechargeConfigId 充值配置ID
	 * @return 充值订单
	 */
	RechargeOrder createOrder(String userId, String rechargeConfigId);

	/**
	 * 支付成功回调
	 * @param orderNo 订单号
	 * @param payOrderNo 支付订单号
	 */
	void paySuccess(String orderNo, String payOrderNo);

	/**
	 * 取消订单
	 * @param orderNo 订单号
	 */
	void cancelOrder(String orderNo);

}
