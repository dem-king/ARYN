package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 专区商品管理分页行。
 *
 * @author aryn
 * @since 2026/11/04
 */
@Data
@Schema(description = "专区商品管理分页行")
public class ZoneGoodsRowVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;

	private String name;

	private String subTitle;

	private String[] spuUrls;

	private String categoryName;

	private String brandName;

	private BigDecimal salesPrice;

	private BigDecimal originalPrice;

	private String status;

	private Integer stock;

	private Integer isMemberItem;

	private BigDecimal memberDiscountRate;

	private BigDecimal memberPriceFixed;

	private BigDecimal memberPriceFloor;

	private Integer isClearanceItem;

	private BigDecimal clearancePrice;

	private BigDecimal clearanceDiscountRate;

	private LocalDateTime clearanceExpireTime;

	@Schema(description = "存在专区规则的 SKU 数")
	private Integer skuOverrideCount;

	@Schema(description = "生效状态：NORMAL / NO_RULE / INVERTED / EXPIRED / OFF_SHELF")
	private String state;

	@Schema(description = "生效状态文案")
	private String stateLabel;

	@Schema(description = "按 SPU 规则计算的生效价（无效时为空）")
	private BigDecimal effectivePrice;

	@Schema(description = "当前专区：member / clearance")
	private String zoneType;

}
