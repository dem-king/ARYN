package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端低价捡漏专区商品（按商品标签选品，价格已按出清规则算好）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "C端低价捡漏专区商品")
public class AppClearanceGoodsVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "选品总数")
	private Long total;

	@Schema(description = "专区排序：default/price_asc/discount_desc")
	private String sortMode;

	@Schema(description = "商品列表")
	private List<Item> items;

	@Data
	@Schema(description = "捡漏商品项")
	public static class Item implements Serializable {

		private static final long serialVersionUID = 1L;

		private String spuId;

		private String name;

		private String subTitle;

		private String picUrl;

		@Schema(description = "当前售价")
		private BigDecimal salesPrice;

		@Schema(description = "原价（划线价）")
		private BigDecimal originalPrice;

		@Schema(description = "出清价")
		private BigDecimal clearancePrice;

		@Schema(description = "出清截止时间，空=长期有效")
		private LocalDateTime clearanceExpireTime;

	}

}
