package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 销售业绩-订单支付成功事件处理
 * <p>
 * 订单带归属销售快照（event.salesStaffId 非空）时生成销售业绩单；
 * 与 {@link DistributionPayHandler} 互斥，同一订单不会同时产生分销佣金和销售提成。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesPayHandler implements PromotionPayEventHandler {

	private final ISalesSettlementService salesSettlementService;

	@Override
	public void handle(OrderPaySuccessEvent event) {
		if (event == null || event.getSalesStaffId() == null || event.getSalesStaffId().isBlank()) {
			return;
		}
		log.info("销售支付事件处理开始 orderId={}, salesStaffId={}, paymentType={}, paymentPrice={}, actualPayPrice={}",
				event.getOrderId(), event.getSalesStaffId(), event.getPaymentType(), event.getPaymentPrice(),
				event.getActualPayPrice());
		Boolean created = salesSettlementService.handlePaySuccess(event);
		log.info("销售支付事件处理完成 orderId={}, created={}", event.getOrderId(), created);
	}

}
