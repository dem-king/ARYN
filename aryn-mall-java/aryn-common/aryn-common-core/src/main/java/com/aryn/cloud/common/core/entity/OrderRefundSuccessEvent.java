
package com.aryn.cloud.common.core.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderRefundSuccessEvent {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "订单主键")
	private String orderId;

	@Schema(description = "订单项主键")
	private String orderItemId;

	@Schema(description = "退款状态")
	private String status;

	@Schema(description = "退款到账状态：0.未退款；1.退款中；2.退款成功；3.退款失败；")
	private String arrivalStatus;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

	@Schema(description = "稳定退款业务号")
	private String refundNo;

	@Schema(description = "退款佣金基数（不含运费）")
	private BigDecimal refundBaseAmount;

	@Schema(description = "退款流水号")
	private String refundTradeNo;

	@Schema(description = "退款原因")
	private String refundReason;

	@Schema(description = "拒绝退款原因")
	private String refuseReason;

	@Schema(description = "退款入账账户")
	private String userReceivedAccount;

	@Schema(description = "退款单号")
	private String refundId;

	@Schema(description = "用户Id")
	private String userId;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "退款类型")
	private String refundType;

	@Schema(description = "用户优惠券id")
	private String couponUserId;

	@Schema(description = "支付类型")
	private String paymentType;

	private OrderItemRefundSuccessEvent orderItem;

	@Schema(description = "购买场景：1海员个人 2船供采购 3企业采购（企业轨成长值回扣使用；空按个人处理）")
	private String purchaseScene;

	@Schema(description = "配送船舶ID（scene=2 时企业轨成长值回扣主体）")
	private String vesselId;

	@Schema(description = "采购主体ID（scene=3 时企业轨成长值回扣主体）")
	private String buyerOrgId;

	@Schema(description = "原订单完成时间（成长值回扣到原归属月份；空回退退款时刻——未收货即退款等场景）")
	private LocalDateTime completeTime;

}
