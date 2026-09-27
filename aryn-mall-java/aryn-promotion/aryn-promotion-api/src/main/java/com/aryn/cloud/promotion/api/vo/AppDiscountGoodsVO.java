package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * C端折扣商品项：折扣活动下的单个商品（已算好折扣价）。
 *
 * <p>与 {@code AppDiscountVO} 的区别：后者是「某个 SKU 当前最优折扣」的扁平结果，
 * 本类只描述「某活动下的某个商品」，价格字段是相对该活动计算出来的，
 * 供 C 端折扣会场按活动分组展示。
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "C端折扣商品项")
public class AppDiscountGoodsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

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

	@Schema(description = "折扣类型:1打折 2减价 3固定价")
	private Integer discountType;

	@Schema(description = "折扣值")
	private BigDecimal discountValue;
}
