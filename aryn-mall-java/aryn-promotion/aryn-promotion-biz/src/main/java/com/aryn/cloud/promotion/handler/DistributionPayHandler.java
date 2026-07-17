
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 分销-订单支付成功事件处理
 * <p>
 * 订单支付成功后自动触发分销归因结算
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionPayHandler implements PromotionPayEventHandler {

	private final IDistributionSettlementService distributionSettlementService;

	@Override
	public void handle(OrderPaySuccessEvent event) {
		log.info("分销支付事件处理开始 orderId={}, userId={}, paymentPrice={}",
			event.getOrderId(), event.getUserId(), event.getPaymentPrice());

		BigDecimal paymentAmount = event.getPaymentPrice() == null ? BigDecimal.ZERO : event.getPaymentPrice();
		BigDecimal freightAmount = event.getFreightPrice() == null ? BigDecimal.ZERO : event.getFreightPrice();
		BigDecimal commissionBaseAmount = paymentAmount.subtract(freightAmount).max(BigDecimal.ZERO);
		DistributionSettleDTO dto = new DistributionSettleDTO();
		dto.setOrderId(event.getOrderId());
		dto.setBuyerUserId(event.getUserId());
		dto.setOrderAmount(commissionBaseAmount);
		dto.setPaymentAmount(paymentAmount);
		dto.setFreightAmount(freightAmount);

		Boolean settled = distributionSettlementService.settleOrder(dto);
		log.info("分销支付事件处理完成 orderId={}, settled={}", event.getOrderId(), settled);
	}

}
