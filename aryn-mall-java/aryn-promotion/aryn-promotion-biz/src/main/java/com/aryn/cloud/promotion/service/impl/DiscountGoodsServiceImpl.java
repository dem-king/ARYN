package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.mapper.DiscountGoodsMapper;
import com.aryn.cloud.promotion.service.IDiscountGoodsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountGoodsServiceImpl extends ServiceImpl<DiscountGoodsMapper, DiscountGoods>
		implements IDiscountGoodsService {

	@Override
	public List<DiscountGoods> listByActivityId(String activityId) {
		return this.list(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getActivityId, activityId));
	}

	@Override
	public List<DiscountGoods> listBySkuId(String skuId) {
		return this.list(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getSkuId, skuId));
	}
}