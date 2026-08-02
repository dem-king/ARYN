package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.remote.RemoteDiscountService;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 限时折扣 Dubbo 远程服务实现
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteDiscountServiceImpl implements RemoteDiscountService {

	private final IDiscountActivityService discountActivityService;

	@Override
	public BigDecimal calculatePrice(String skuId, BigDecimal originalPrice) {
		return discountActivityService.calculatePrice(skuId, originalPrice);
	}

	@Override
	public boolean hasDiscount(String skuId) {
		return discountActivityService.getGoodsDiscountInfo(skuId) != null;
	}
}