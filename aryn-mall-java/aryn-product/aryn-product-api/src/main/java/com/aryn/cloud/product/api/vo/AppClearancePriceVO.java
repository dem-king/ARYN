package com.aryn.cloud.product.api.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端批量出清价（商详/购物车展示用，也供商品列表按 SPU 聚合展示）。
 *
 * <p>只下发已生效且真实低于当前售价的出清价；到期、倒挂、未打标的 SKU 不会出现在列表里，
 * 前端未命中即按原价展示。
 *
 * <p>两种入参由 Controller 按「skuIds 优先，无 skuIds 时用 spuIds」分流：
 * 按 skuIds 查询时 {@link Item#getSkuId()} 精确命中；按 spuIds 查询时返回请求 SPU 下
 * 每个有效 SKU 一条（{@link Item#getSpuId()} 回填），由 C 端按卡片展示价对应的 SKU 聚合最低价。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@Schema(description = "C端批量出清价")
public class AppClearancePriceVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "出清价列表")
	private List<Item> items;

	@Data
	@Schema(description = "单 SKU 出清价")
	public static class Item implements Serializable {

		private static final long serialVersionUID = 1L;

		private String skuId;

		/**
		 * SPU ID：按 spuIds 查询时回填，供列表卡片按 SPU 聚合；旧 skuIds 查询不序列化该字段（契约不变）。
		 */
		@JsonInclude(JsonInclude.Include.NON_NULL)
		@Schema(description = "SPU ID（按 spuIds 查询时回填；按 skuIds 查询不返回）")
		private String spuId;

		@Schema(description = "当前售价（划线基准）")
		private BigDecimal salesPrice;

		@Schema(description = "有效出清价")
		private BigDecimal clearancePrice;

		@Schema(description = "出清截止时间，空=长期有效")
		private LocalDateTime clearanceExpireTime;

	}

}
