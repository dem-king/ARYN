package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 订单提交后通知工作人员处理，不给下单用户本人发站内信。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedMessageNotifier {

	private final OrderStaffNotifyService orderStaffNotifyService;

	/**
	 * 工作人员通知：新订单待处理。
	 *
	 * <p>与买家无关：买家刚完成下单动作，页面已有结果反馈，再发「订单已提交」
	 * 只是重复告知本人已做的事。通知服务内部不感知事务（本方法已在提交后执行），
	 * 失败只告警，不影响订单事实。
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void notifyStaff(ArynOrderCreateAfterEvent event) {
		try {
			orderStaffNotifyService.notifyOrderCreated(event.getOrderInfo());
		}
		catch (RuntimeException exception) {
			OrderInfo order = event.getOrderInfo();
			log.error("新订单工作人员通知发送失败，不回滚订单, orderId={}", order == null ? null : order.getId(),
					exception);
		}
	}

}
