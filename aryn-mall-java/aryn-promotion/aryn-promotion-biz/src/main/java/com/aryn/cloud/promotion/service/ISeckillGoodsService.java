package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;

import java.util.List;

public interface ISeckillGoodsService extends IService<SeckillGoods> {

	/**
	 * 根据场次ID查询秒杀商品
	 */
	List<SeckillGoods> listBySessionId(String sessionId);

	/**
	 * 根据SKU ID查询当前进行中的秒杀商品
	 */
	SeckillGoods getBySkuId(String skuId);
}