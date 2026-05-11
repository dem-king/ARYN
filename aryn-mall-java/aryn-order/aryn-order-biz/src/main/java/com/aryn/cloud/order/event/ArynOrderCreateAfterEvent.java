
package com.aryn.cloud.order.event;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 订单创建后事件
 *
 * @author: aryn
 * @date: 2024/6/01 11:57
 */
public class ArynOrderCreateAfterEvent extends ApplicationEvent {

	@Getter
	private final OrderInfo orderInfo;

	@Getter
	private final List<OrderItemEntity> orderItemEntityList;

	/**
	 * 订单创建方式：1.购物车下单；2.普通购买下单
	 */
	@Getter
	private final String createWay;

	public ArynOrderCreateAfterEvent(Object source, OrderInfo orderInfo, List<OrderItemEntity> orderItemEntityList,
			String createWay) {
		super(source);
		this.orderInfo = orderInfo;
		this.orderItemEntityList = orderItemEntityList;
		this.createWay = createWay;
	}

}
