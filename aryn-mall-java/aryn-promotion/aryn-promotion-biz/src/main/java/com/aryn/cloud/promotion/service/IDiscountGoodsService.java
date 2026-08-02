package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;

import java.util.List;

public interface IDiscountGoodsService extends IService<DiscountGoods> {

	/**
	 * 根据活动ID查询折扣商品
	 */
	List<DiscountGoods> listByActivityId(String activityId);

	/**
	 * 根据SKU ID查询参与的折扣活动
	 */
	List<DiscountGoods> listBySkuId(String skuId);
}