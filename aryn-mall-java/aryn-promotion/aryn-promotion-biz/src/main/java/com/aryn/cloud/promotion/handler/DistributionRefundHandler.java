
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 分销-订单退款成功事件处理
 * <p>
 * 退款成功后按比例回退已结算佣金
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributionRefundHandler implements PromotionRefundEventHandler {

	private final IDistributionSettlementService distributionSettlementService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		log.info("分销退款事件处理开始 orderId={}, refundNo={}, refundBaseAmount={}",
			event.getOrderId(), event.getRefundNo(), event.getRefundBaseAmount());

		distributionSettlementService.refundCommission(
			event.getOrderId(), event.getRefundNo(), event.getRefundAmount(), event.getRefundBaseAmount());
		log.info("分销退款事件处理完成 orderId={}", event.getOrderId());
	}

}
