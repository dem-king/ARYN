
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

	/**
	 * 归属销售ID快照（下单时写入；为空表示该订单不计销售业绩）。
	 * <p>由 {@code BeanUtils.copyProperties(orderInfo, event)} 自动带出，字段名须与 OrderInfo 一致。
	 */
	private String salesStaffId;

	/**
	 * 实收金额（货到付款确认收款时登记；在线支付为空）。
	 * <p>销售提成按实付金额计算：在线支付取 paymentPrice，货到付款取 actualPayPrice。
	 */
	private BigDecimal actualPayPrice;

}
