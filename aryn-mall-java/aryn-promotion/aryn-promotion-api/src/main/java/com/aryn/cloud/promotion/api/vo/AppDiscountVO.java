package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "C端折扣商品VO")
public class AppDiscountVO {

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "活动名称")
	private String activityName;

	@Schema(description = "折扣类型:1打折 2减价 3固定价")
	private Integer discountType;

	@Schema(description = "折扣值")
	private BigDecimal discountValue;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "商品名称")
	private String goodsName;

	@Schema(description = "商品图片")
	private String goodsImage;

	@Schema(description = "原价")
	private BigDecimal originalPrice;

	@Schema(description = "折扣价")
	private BigDecimal discountPrice;
}