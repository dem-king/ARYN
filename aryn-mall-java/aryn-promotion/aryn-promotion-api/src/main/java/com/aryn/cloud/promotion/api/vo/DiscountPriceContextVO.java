package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 限时折扣价上下文 VO（订单侧计价用：价格 + 活动叠加策略）。
 *
 * @author aryn
 * @since 2026/10/24
 */
@Data
@Schema(description = "限时折扣价上下文VO")
public class DiscountPriceContextVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "折扣价（null 表示无进行中的折扣）")
	private BigDecimal discountPrice;

	@Schema(description = "活动商品是否可用优惠券")
	private Boolean couponStackable;

	@Schema(description = "活动商品是否可积分抵现")
	private Boolean pointsStackable;

}
