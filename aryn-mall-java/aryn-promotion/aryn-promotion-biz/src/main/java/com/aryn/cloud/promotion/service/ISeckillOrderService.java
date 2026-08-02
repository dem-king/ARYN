package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;

import java.math.BigDecimal;
import java.util.List;

public interface ISeckillOrderService extends IService<SeckillOrder> {

	/**
	 * 秒杀下单：预扣 Redis 库存 + 创建预扣记录（状态=未支付）
	 *
	 * @param dto     下单参数
	 * @param userId  用户ID
	 * @param orderId 业务订单ID
	 * @return 秒杀总价（单价 * 数量）；null 表示失败
	 */
	BigDecimal createSeckillOrder(SeckillOrderDTO dto, String userId, String orderId);

	/**
	 * 回滚预扣库存（下单失败/取消时调用）
	 *
	 * @param orderId 业务订单ID
	 * @return true=回滚成功
	 */
	boolean rollbackByOrderId(String orderId);

	/**
	 * 支付成功：确认扣减DB库存、更新已售数（幂等，基于状态机 CAS）
	 */
	void handlePaySuccess(String orderId);

	/**
	 * 退款成功：回滚Redis和DB库存（幂等，基于状态机 CAS）
	 */
	void handleRefundSuccess(String orderId);

	/**
	 * 查询超时未支付的预扣订单（供定时任务调用）
	 *
	 * @param expireMinutes 超时分钟数
	 * @return 超时订单列表
	 */
	List<SeckillOrder> listExpiredUnpaid(int expireMinutes);

	/**
	 * 标记订单超时并回滚库存
	 *
	 * @param orderId 业务订单ID
	 * @return true=处理成功
	 */
	boolean expireOrder(String orderId);
}
