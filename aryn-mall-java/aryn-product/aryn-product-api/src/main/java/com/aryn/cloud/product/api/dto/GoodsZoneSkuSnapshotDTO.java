package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专区商品 SKU 快照：供促销域同步系统托管活动使用。
 *
 * <p>所有价格字段均为 SKU 级「生效值」：SKU 覆盖优先，空则回落 SPU。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "专区商品SKU快照")
public class GoodsZoneSkuSnapshotDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String skuId;

	private String spuId;

	private String spuName;

	@Schema(description = "SKU 当前售价")
	private BigDecimal salesPrice;

	@Schema(description = "SKU 当前原价")
	private BigDecimal originalPrice;

	@Schema(description = "SKU 成本价（会员价按成本兜底用，不下发 C 端）")
	private BigDecimal costPrice;

	@Schema(description = "是否会员商品（生效值）")
	private Boolean memberItem;

	@Schema(description = "会员折扣率（生效值）")
	private BigDecimal memberDiscountRate;

	@Schema(description = "会员固定价（生效值）")
	private BigDecimal memberPriceFixed;

	@Schema(description = "会员价下限（生效值）")
	private BigDecimal memberPriceFloor;

	@Schema(description = "是否出清商品（生效值）")
	private Boolean clearanceItem;

	@Schema(description = "出清固定价（生效值）")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率（生效值）")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间（生效值）")
	private LocalDateTime clearanceExpireTime;

}
