package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话上下文中的最近订单项。
 *
 * <p>字段与订单域 {@code order.api.vo.OrderContextVO} 一一对应（message-api 不依赖 order-api，
 * 由 message-biz 做一次映射）；字段允许为空以支持降级。</p>
 */
@Data
public class OrderContextItemVO implements Serializable {

	private String orderId;
	private String orderNo;

	/** 订单状态码：1 待付款 / 2 待发货 / 3 待收货 / 4 已完成 / 5 退款中 / 11 已取消。 */
	private String status;

	/** 状态中文名；未知状态码为 null（前端回退展示 status）。 */
	private String statusName;

	/** 支付状态码：0 未支付 / 1 已支付。 */
	private String payStatus;

	/** 支付金额：优先实收金额，缺失回退应付金额；均空为 null。 */
	private BigDecimal payAmount;

	private LocalDateTime createTime;

	/** 商品摘要；订单域本批次降级为 null。 */
	private String productSummary;

	/** 配送方式码：1 普通快递 / 2 上门自提。 */
	private String deliveryWay;

}
