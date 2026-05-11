
package com.aryn.cloud.order.event;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
public class ArynOrderRefundEvent extends ApplicationEvent {

	@Getter
	private final OrderRefund orderRefund;

	@Getter
	private final OrderItemEntity orderItemEntity;

	@Getter
	private final OrderInfo orderInfo;

	public ArynOrderRefundEvent(Object source, OrderRefund orderRefund, OrderItemEntity orderItemEntity,
			OrderInfo orderInfo) {
		super(source);
		this.orderRefund = orderRefund;
		this.orderItemEntity = orderItemEntity;
		this.orderInfo = orderInfo;
	}

}
