package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.remote.RemotePromotionEngine;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.aryn.cloud.promotion.service.PromotionReservationService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 统一营销引擎远程实现。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemotePromotionEngineImpl implements RemotePromotionEngine {

	private final PromotionEngineService promotionEngineService;

	private final PromotionReservationService promotionReservationService;

	@Override
	public PromotionCalculationVO preview(PromotionContextDTO context) {
		return promotionEngineService.preview(context);
	}

	@Override
	public PromotionCalculationVO reserve(PromotionContextDTO context) {
		return promotionReservationService.reserve(context);
	}

	@Override
	public void confirm(String tenantId, String orderId) {
		promotionReservationService.confirm(tenantId, orderId);
	}

	@Override
	public void release(String tenantId, String orderId, String reason) {
		promotionReservationService.release(tenantId, orderId, reason);
	}

}
