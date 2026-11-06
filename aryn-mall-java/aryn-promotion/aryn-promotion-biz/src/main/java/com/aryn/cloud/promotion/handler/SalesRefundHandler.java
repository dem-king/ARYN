package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 销售业绩-订单退款成功事件处理
 * <p>
 * 按退款基数比例扣回提成；未发放直接冲减、已发放生成负数结转下月台账。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesRefundHandler implements PromotionRefundEventHandler {

	private final ISalesSettlementService salesSettlementService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		if (event == null || event.getOrderId() == null || event.getOrderId().isBlank()) {
			return;
		}
		log.info("销售退款事件处理开始 orderId={}, refundNo={}, refundAmount={}, refundBaseAmount={}",
				event.getOrderId(), event.getRefundNo(), event.getRefundAmount(), event.getRefundBaseAmount());
		Boolean applied = salesSettlementService.handleRefund(event);
		log.info("销售退款事件处理完成 orderId={}, refundNo={}, applied={}", event.getOrderId(),
				event.getRefundNo(), applied);
	}

}
