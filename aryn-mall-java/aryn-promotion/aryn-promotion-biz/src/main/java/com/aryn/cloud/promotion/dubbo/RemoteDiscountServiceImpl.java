package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.dto.DiscountStockReserveResultDTO;
import com.aryn.cloud.promotion.api.remote.RemoteDiscountService;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.service.DiscountStockService;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 限时折扣 Dubbo 远程服务实现
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteDiscountServiceImpl implements RemoteDiscountService {

	private final IDiscountActivityService discountActivityService;

	private final DiscountStockService discountStockService;

	@Override
	public BigDecimal calculatePrice(String skuId, BigDecimal originalPrice) {
		return discountActivityService.calculatePrice(skuId, originalPrice);
	}

	@Override
	public boolean hasDiscount(String skuId) {
		return discountActivityService.getGoodsDiscountInfo(skuId) != null;
	}

	@Override
	public DiscountStockReserveResultDTO reserveStock(String tenantId, String orderId,
			List<DiscountStockItemDTO> items) {
		try {
			return discountStockService.reserve(tenantId, orderId, items);
		} catch (ArynBusinessException business) {
			// 库存不足是预期分支：转成结果对象，避免被 Dubbo 包成 RpcException
			return DiscountStockReserveResultDTO.fail(null, business.getMsg());
		}
	}

	@Override
	public int releaseStock(String tenantId, String orderId, String orderItemId, String reason) {
		return discountStockService.release(tenantId, orderId, orderItemId, reason);
	}
}