package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品专区标签回显（管理端表单/精调）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "商品专区标签回显")
public class GoodsZoneTagVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String spuId;

	private String spuName;

	private Integer isMemberItem;

	private BigDecimal memberDiscountRate;

	private BigDecimal memberPriceFixed;

	private BigDecimal memberPriceFloor;

	private Integer isClearanceItem;

	private BigDecimal clearancePrice;

	private BigDecimal clearanceDiscountRate;

	private LocalDateTime clearanceExpireTime;

	@Schema(description = "SKU 级覆盖")
	private List<SkuTagVO> skus;

	@Schema(description = "存在专区规则的 SKU 数")
	private Integer skuOverrideCount;

	@Schema(description = "规则/生效告警")
	private List<ZoneTagWarningVO> warnings;

	@Data
	@Schema(description = "SKU 专区标签覆盖")
	public static class SkuTagVO implements Serializable {

		private static final long serialVersionUID = 1L;

		private String skuId;

		private String specsName;

		private BigDecimal salesPrice;

		private BigDecimal originalPrice;

		private Integer isMemberItem;

		private BigDecimal memberDiscountRate;

		private BigDecimal memberPriceFixed;

		private BigDecimal memberPriceFloor;

		private Integer isClearanceItem;

		private BigDecimal clearancePrice;

		private BigDecimal clearanceDiscountRate;

		private LocalDateTime clearanceExpireTime;

		@Schema(description = "是否存在任一专区覆盖")
		private Boolean hasOverride;

	}


}
