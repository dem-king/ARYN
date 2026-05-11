
package com.aryn.cloud.common.core.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单支付成功内部事件实体
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */

@Data
public class OrderPaySuccessEvent implements Serializable {

	private String orderId;

	private String orderNo;

	private String userId;

	private String paymentType;

	private BigDecimal paymentPrice;

	private BigDecimal freightPrice;

	private String couponUserId;

	private String tenantId;

	private BigDecimal totalPrice;

	private LocalDateTime paymentTime;

	private List<OrderItemPaySuccessEvent> itemList;

}
