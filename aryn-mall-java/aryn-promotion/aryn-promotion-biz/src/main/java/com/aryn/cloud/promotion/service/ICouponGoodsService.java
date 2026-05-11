
package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.CouponGoods;

import java.util.List;

public interface ICouponGoodsService extends IService<CouponGoods> {

	List<CouponGoods> getByCouponId(String couponId);

}
