package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "折扣商品关联VO")
public class DiscountGoodsVO {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "商品图片")
	private String goodsImage;

	@Schema(description = "商品销售价")
	private BigDecimal salesPrice;
}