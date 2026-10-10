package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.dto.RechargePrepayDTO;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.api.vo.AppRechargeOrderVO;

import java.util.Map;

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
	 * 发起充值预支付（复用订单支付链路，回调经 pay-notify-topic 回到本模块）
	 * @param userId 用户ID
	 * @param prepayDTO 预支付参数
	 * @return orderNo + payParams
	 */
	Map<String, Object> prepay(String userId, RechargePrepayDTO prepayDTO);

	/**
	 * 支付成功回调
	 * @param orderNo 订单号
	 * @param payOrderNo 支付订单号
	 */
	void paySuccess(String orderNo, String payOrderNo);

	/**
	 * 查询充值单并核对支付结果。
	 *
	 * <p>C 端支付结果页轮询本方法：本地仍是待支付时，请支付域向渠道查单取权威结论，
	 * 确认成功则立即入账，使展示不依赖支付回调的到达时机。
	 *
	 * @param userId 用户ID（只允许查本人订单）
	 * @param orderNo 充值订单号
	 * @return 充值单信息（含渠道交易状态）；订单不存在或不属于该用户时返回 null
	 */
	AppRechargeOrderVO queryAndSettle(String userId, String orderNo);

	/**
	 * 取消订单
	 * @param orderNo 订单号
	 */
	void cancelOrder(String orderNo);

}
