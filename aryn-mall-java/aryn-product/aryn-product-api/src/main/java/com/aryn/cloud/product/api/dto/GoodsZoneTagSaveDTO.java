package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个商品的专区标签精调请求：SPU 级规则 + 可选 SKU 级覆盖。
 *
 * <p>SKU 条目中字段为 null 表示「继承/不修改」；isMemberItem/isClearanceItem
 * 显式传 0/1 才改 SKU 覆盖，传 null 保持原值。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "单个商品的专区标签精调请求")
public class GoodsZoneTagSaveDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "会员专区标签")
	private Boolean memberTag;

	@Schema(description = "商品级会员折扣率")
	private BigDecimal memberDiscountRate;

	@Schema(description = "会员固定价")
	private BigDecimal memberPriceFixed;

	@Schema(description = "会员价下限")
	private BigDecimal memberPriceFloor;

	@Schema(description = "捡漏专区标签")
	private Boolean clearanceTag;

	@Schema(description = "出清固定价")
	private BigDecimal clearancePrice;

	@Schema(description = "出清折扣率")
	private BigDecimal clearanceDiscountRate;

	@Schema(description = "出清截止时间")
	private LocalDateTime clearanceExpireTime;

	@Schema(description = "显式清空 SPU 会员规则（折扣率/固定价/下限），不影响标签")
	private Boolean clearMemberRule;

	@Schema(description = "显式清空 SPU 出清规则（价格/折扣率/截止时间），不影响标签")
	private Boolean clearClearanceRule;

	@Schema(description = "SKU 覆盖精确提交：true 时 skus 中出现的 SKU 按提交值覆盖（null=清空该字段并恢复继承）")
	private Boolean replaceSkuRules;

	@Schema(description = "SKU 级覆盖")
	private List<SkuTag> skus;

	@Data
	@Schema(description = "SKU 级专区标签覆盖")
	public static class SkuTag implements Serializable {

		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID", requiredMode = Schema.RequiredMode.REQUIRED)
		private String skuId;

		@Schema(description = "会员标签覆盖：0否 1是；null=继承 SPU")
		private Integer memberTag;

		@Schema(description = "会员折扣率覆盖")
		private BigDecimal memberDiscountRate;

		@Schema(description = "会员固定价覆盖")
		private BigDecimal memberPriceFixed;

		@Schema(description = "会员价下限覆盖")
		private BigDecimal memberPriceFloor;

		@Schema(description = "出清标签覆盖：0否 1是；null=继承 SPU")
		private Integer clearanceTag;

		@Schema(description = "出清固定价覆盖")
		private BigDecimal clearancePrice;

		@Schema(description = "出清折扣率覆盖")
		private BigDecimal clearanceDiscountRate;

		@Schema(description = "出清截止时间覆盖")
		private LocalDateTime clearanceExpireTime;

	}

}
