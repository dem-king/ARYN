package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SPU 级专区标签与规则快照。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "商品专区标签与规则快照")
public class ZoneRuleSnapshotVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "会员专区标签：0否 1是")
	private Integer memberTag;

	@Schema(description = "会员折扣率")
	private BigDecimal memberDiscountRate;

	@Schema(description = "会员固定价")
	private BigDecimal memberPriceFixed;

	@Schema(description = "会员价下限")
	private BigDecimal memberPriceFloor;

	@Schema(description = "出清专区标签：0否 1是")
	private Integer clearanceTag;

	@Schema(description = "出清固定价")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间")
	private LocalDateTime clearanceExpireTime;

	@Schema(description = "存在专区规则的 SKU 数")
	private Integer skuOverrideCount;

}
