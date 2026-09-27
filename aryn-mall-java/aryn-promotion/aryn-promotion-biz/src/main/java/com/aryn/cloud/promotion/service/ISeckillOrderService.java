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
	 * <p>同一订单可能包含多个秒杀商品（多条预扣记录），全部回滚。
	 *
	 * @param orderId 业务订单ID
	 * @return true=至少回滚一条
	 */
	boolean rollbackByOrderId(String orderId);

	/**
	 * 按预扣参数释放库存（下单事务回滚时调用，幂等）。
	 *
	 * <p>预扣记录随下单事务一起回滚时无法按订单号找回，只能凭参数释放 Redis 库存。
	 *
	 * @param dto    预扣参数
	 * @param userId 用户ID
	 * @param orderId 业务订单ID
	 * @return true=已释放
	 */
	boolean releaseDeduct(SeckillOrderDTO dto, String userId, String orderId);

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
