
package com.aryn.cloud.order.event;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
public class ArynOrderPayEvent extends ApplicationEvent {

	@Getter
	private final OrderInfo order;

	@Getter
	private final List<OrderItemEntity> orderItemEntityList;

	public ArynOrderPayEvent(Object source, OrderInfo order, List<OrderItemEntity> orderItemEntityList) {
		super(source);
		this.order = order;
		this.orderItemEntityList = orderItemEntityList;
	}

}
