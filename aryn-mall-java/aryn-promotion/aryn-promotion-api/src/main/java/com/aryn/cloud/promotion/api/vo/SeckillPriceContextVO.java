package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀价上下文 VO（订单侧计价用：价格 + 活动叠加策略）。
 *
 * <p>订单侧对秒杀类型按 {@link com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum#SECKILL}
 * 强制不可叠（防配置错误），不信任本 VO 的叠加字段。
 *
 * @author aryn
 * @since 2026/10/24
 */
@Data
@Schema(description = "秒杀价上下文VO")
public class SeckillPriceContextVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "秒杀价（null 表示无进行中的秒杀）")
	private BigDecimal seckillPrice;

	@Schema(description = "活动商品是否可用优惠券（订单侧对秒杀强制 false）")
	private Boolean couponStackable;

	@Schema(description = "活动商品是否可积分抵现（订单侧对秒杀强制 false）")
	private Boolean pointsStackable;

}
