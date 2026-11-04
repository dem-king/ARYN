package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU 级专区标签与规则状态。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "SKU 专区标签与规则状态")
public class ZoneSkuStateVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "SKU ID")
	private String skuId;

	@Schema(description = "规格名称")
	private String specsName;

	@Schema(description = "售价")
	private BigDecimal salesPrice;

	@Schema(description = "原价")
	private BigDecimal originalPrice;

	@Schema(description = "会员标签覆盖：null=继承 0否 1是")
	private Integer isMemberItem;

	@Schema(description = "会员折扣率覆盖")
	private BigDecimal memberDiscountRate;

	@Schema(description = "会员固定价覆盖")
	private BigDecimal memberPriceFixed;

	@Schema(description = "会员价下限覆盖")
	private BigDecimal memberPriceFloor;

	@Schema(description = "出清标签覆盖：null=继承 0否 1是")
	private Integer isClearanceItem;

	@Schema(description = "出清固定价覆盖")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率覆盖")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间覆盖")
	private LocalDateTime clearanceExpireTime;

	@Schema(description = "是否存在任一专区覆盖")
	private Boolean hasOverride;

}
