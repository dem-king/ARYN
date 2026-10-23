package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 折扣库存占用项（下单 → 营销域的远程入参）。
 */
@Data
@Schema(description = "折扣库存占用项")
public class DiscountStockItemDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "购买数量")
	private Integer quantity;

	public DiscountStockItemDTO() {
	}

	public DiscountStockItemDTO(String skuId, Integer quantity) {
		this.skuId = skuId;
		this.quantity = quantity;
	}

}