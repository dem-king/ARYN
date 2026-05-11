
package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.CouponGoods;
import com.aryn.cloud.promotion.mapper.CouponGoodsMapper;
import com.aryn.cloud.promotion.service.ICouponGoodsService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CouponGoodsServiceImpl extends ServiceImpl<CouponGoodsMapper, CouponGoods> implements ICouponGoodsService {

	@Override
	public List<CouponGoods> getByCouponId(String couponId) {
		return baseMapper.selectByCouponId(couponId);
	}

}
