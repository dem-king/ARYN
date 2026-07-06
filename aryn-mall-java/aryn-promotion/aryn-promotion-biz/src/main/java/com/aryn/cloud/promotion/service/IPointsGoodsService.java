
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.PointsExchangeRecord;
import com.aryn.cloud.promotion.api.entity.PointsGoods;

/**
 * 积分商品 Service
 *
 * @author aryn
 */
public interface IPointsGoodsService extends IService<PointsGoods> {

	/**
	 * 积分兑换商品
	 * @param userId 用户ID
	 * @param goodsId 积分商品ID
	 * @return 兑换记录
	 */
	PointsExchangeRecord exchangePointsGoods(String userId, String goodsId);

}