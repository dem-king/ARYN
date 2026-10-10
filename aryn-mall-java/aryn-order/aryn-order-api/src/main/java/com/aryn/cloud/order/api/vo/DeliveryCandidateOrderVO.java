
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 司机可拉进当前趟次的候选订单（配货页选单面板的一行）。
 *
 * <p>两种来源共用这一个 VO：
 * <ul>
 *   <li>来源=我的未完成：这单已有配送任务且已派给当前司机，只是不在本趟车上</li>
 *   <li>来源=未派送：已付款待发货的同租户订单，可能尚无配送任务</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "司机可拉入趟次的候选订单")
public class DeliveryCandidateOrderVO {

	@Schema(description = "订单ID")
	private String orderId;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "来源：MINE 我的未完成任务 UNASSIGNED 未派送订单")
	private String source;

	@Schema(description = "已有配送任务ID（未派送且无任务时为空）")
	private String taskId;

	@Schema(description = "已有任务状态（无任务时为空）")
	private String taskStatus;

	@Schema(description = "已有任务所在出车单ID（未在任何趟次时为空）")
	private String tripId;

	@Schema(description = "已有任务所在出车单号（解析失败时为空）")
	private String tripNo;

	@Schema(description = "收货人姓名")
	private String recipientName;

	@Schema(description = "收货人电话")
	private String recipientPhone;

	@Schema(description = "收货完整地址（船供单可能为空，前端回落港口+泊位）")
	private String recipientAddress;

	@Schema(description = "配送船舶名称快照")
	private String vesselName;

	@Schema(description = "港口名称快照")
	private String portName;

	@Schema(description = "泊位快照")
	private String berth;

	@Schema(description = "订单金额")
	private BigDecimal paymentPrice;

	@Schema(description = "配送方式：3商城配送 4公司内部配送")
	private String deliveryWay;

	@Schema(description = "支付方式：3货到付款（司机需收款）")
	private String paymentType;

	@Schema(description = "支付状态：1已付款 0未付款（货到付款在送达前恒为0）")
	private String payStatus;

	@Schema(description = "下单时间")
	private LocalDateTime createTime;

	@Schema(description = "商品件数合计（按订单明细数量求和）")
	private Integer itemCount;

	@Schema(description = "商品明细摘要（取前若干项，供司机认单）")
	private List<DeliveryCandidateItemVO> items;

}
