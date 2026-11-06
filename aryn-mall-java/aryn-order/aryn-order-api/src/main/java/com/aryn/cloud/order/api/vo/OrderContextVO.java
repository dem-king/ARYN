package com.aryn.cloud.order.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员最近订单上下文（客服工作台信息面板用）。
 *
 * <p>由 {@code RemoteOrderContextService#queryRecentOrders} 返回，只读、租户+会员隔离；
 * 字段允许为空以支持降级（如订单无支付金额时 {@code payAmount} 为空、商品摘要本批次降级为 null）。</p>
 */
@Data
public class OrderContextVO implements Serializable {

	/** 订单 ID。 */
	private String orderId;

	/** 订单单号。 */
	private String orderNo;

	/** 订单状态码（1 待付款 / 2 待发货 / 3 待收货 / 4 已完成 / 5 退款中 / 11 已取消）。 */
	private String status;

	/** 订单状态中文名；状态码无法识别时为 null（前端回退展示 status）。 */
	private String statusName;

	/** 支付状态码：0 未支付 / 1 已支付。 */
	private String payStatus;

	/** 支付金额：优先实收金额（actualPayPrice），为空时取应付支付金额（paymentPrice）；均空为 null。 */
	private BigDecimal payAmount;

	/** 下单时间。 */
	private LocalDateTime createTime;

	/**
	 * 商品摘要（如「首件商品名 共 N 件」）。
	 *
	 * <p>本批次为控制消息域聚合接口时延，商品明细跨表查询成本较高，固定降级为 null；
	 * 前端可用订单号/金额/状态展示，后续需要时在订单域单独立项补齐（不扩展既有 mapper XML）。</p>
	 */
	private String productSummary;

	/** 配送方式码：1 普通快递 / 2 上门自提（前端自行映射文案）。 */
	private String deliveryWay;

}
