
package com.aryn.cloud.order.event;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 订单创建前事件
 *
 * @author: aryn
 * @date: 2024/6/01 11:57
 */
public class ArynOrderCreateBeforeEvent extends ApplicationEvent {

	@Getter
	private final OrderInfo orderInfo;

	@Getter
	private final List<OrderItemEntity> orderItemEntityList;

	public ArynOrderCreateBeforeEvent(Object source, OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList) {
		super(source);
		this.orderInfo = orderInfo;
		this.orderItemEntityList = orderItemEntityList;
	}

}
