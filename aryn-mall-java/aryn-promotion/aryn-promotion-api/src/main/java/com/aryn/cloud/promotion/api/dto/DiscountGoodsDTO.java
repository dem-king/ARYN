package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "折扣商品DTO")
public class DiscountGoodsDTO {

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;
}