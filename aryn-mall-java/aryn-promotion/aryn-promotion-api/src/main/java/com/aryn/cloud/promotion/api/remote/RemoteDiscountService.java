package com.aryn.cloud.promotion.api.remote;

import java.math.BigDecimal;

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
	 * 查询 SKU 是否有进行中的折扣活动
	 *
	 * @param skuId SKU ID
	 * @return true=有折扣
	 */
	boolean hasDiscount(String skuId);
}