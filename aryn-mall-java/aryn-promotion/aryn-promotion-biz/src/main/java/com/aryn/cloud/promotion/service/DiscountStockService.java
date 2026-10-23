package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.dto.DiscountStockReserveResultDTO;

import java.util.List;

/**
 * 折扣商品库存占用（商品级真限量）。
 *
 * <p>与秒杀的 Redis 预扣不同：折扣是长周期活动、并发压力小，直接用
 * `UPDATE ... WHERE sold_count + qty <= stock` 的行级锁保证不超卖，少一层缓存一致性。
 * 占用明细落 `discount_stock_lock`，唯一键保证重复调用幂等。
 */
public interface DiscountStockService {

	/**
	 * 下单占用。逐 SKU 判断：未配置 stock（不限量）直接跳过、已占用过则幂等跳过。
	 *
	 * @param tenantId 租户ID（远程调用不能依赖线程上下文）
	 * @param orderId 订单ID
	 * @param items SKU + 数量
	 * @return 成功/失败结果；失败时 message 可直接展示给用户
	 */
	DiscountStockReserveResultDTO reserve(String tenantId, String orderId, List<DiscountStockItemDTO> items);

	/**
	 * 取消/超时/退款释放：按占用明细回补 sold_count，并把明细置为已释放（幂等）。
	 *
	 * @return 实际释放的明细行数
	 */
	int release(String tenantId, String orderId, String reason);

}