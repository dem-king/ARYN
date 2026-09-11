package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一营销计算上下文。
 *
 * <p>订单服务只提交上下文并接收计算结果，不感知具体活动类型；
 * 新增活动类型时通过活动配置表达适用条件，不在订单侧增加 if/else。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "营销计算上下文DTO")
public class PromotionContextDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "下单用户ID")
	private String userId;

	@Schema(description = "订单ID（reserve 时必填）")
	private String orderId;

	@Schema(description = "订单号（reserve 时选填）")
	private String orderNo;

	@Schema(description = "购买场景：1个人 2船供")
	private String purchaseScene;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "靠港计划ID")
	private String vesselCallId;

	@Schema(description = "港口编码")
	private String portCode;

	@Schema(description = "活动时间（默认当前）")
	private LocalDateTime activityTime;

	@Schema(description = "SKU 明细（结算基价）")
	private List<SkuItem> skuItems;

	@Data
	@Schema(description = "营销上下文 SKU 明细")
	public static class SkuItem implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "数量（采购单位）")
		private Integer quantity;

		@Schema(description = "结算基价（未优惠单价）")
		private BigDecimal salesPrice;

	}

}
