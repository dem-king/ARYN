package com.aryn.cloud.promotion.api.remote;

import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.dto.DiscountStockReserveResultDTO;
import com.aryn.cloud.promotion.api.vo.DiscountPriceContextVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 限时折扣 Dubbo 远程服务接口
 */
public interface RemoteDiscountService {

	/**
	 * 计算 SKU 折扣后价格
	 *
	 * @param skuId         SKU ID
	 * @param originalPrice 原价
	 * @return 折扣价（无活动则返回原价）
	 */
	BigDecimal calculatePrice(String skuId, BigDecimal originalPrice);

	/**
	 * 查询 SKU 折扣价上下文（价格 + 活动叠加策略）。
	 *
	 * @param skuId         SKU ID
	 * @param originalPrice 原价
	 * @return 上下文；null 表示无进行中的折扣（调用方回落原价）
	 */
	DiscountPriceContextVO getPriceContext(String skuId, BigDecimal originalPrice);

	/**
	 * 查询 SKU 是否有进行中的折扣活动
	 *
	 * @param skuId SKU ID
	 * @return true=有折扣
	 */
	boolean hasDiscount(String skuId);

	/**
	 * 下单占用折扣商品库存（商品级真限量）。
	 *
	 * <p>只对配置了 stock 的商品生效；未配置（不限量）与已占用过的订单会直接跳过。
	 * 返回结果对象而非抛异常：跨进程异常会被 Dubbo 包装，下单侧无法区分
	 * 「库存不足」与「营销服务不可用」，会把拦截静默吞掉。
	 *
	 * @param tenantId 租户ID（远程调用不依赖线程上下文）
	 * @param orderId 订单ID（幂等键之一）
	 * @param items SKU + 购买数量
	 * @return success=false 时 message 可直接展示给用户
	 */
	DiscountStockReserveResultDTO reserveStock(String tenantId, String orderId, List<DiscountStockItemDTO> items);

	/**
	 * 释放订单占用的折扣库存，幂等。
	 *
	 * @param orderItemId 非空时只释放该明细（部分退款）；为空时释放整单（取消/超时）
	 * @return 实际释放的明细行数
	 */
	int releaseStock(String tenantId, String orderId, String orderItemId, String reason);
}