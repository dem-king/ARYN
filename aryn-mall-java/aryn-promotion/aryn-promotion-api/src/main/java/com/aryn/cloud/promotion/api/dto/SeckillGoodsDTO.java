package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "秒杀商品DTO")
public class SeckillGoodsDTO {

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	@Schema(description = "秒杀价")
	@NotNull(message = "秒杀价不能为空")
	@Positive(message = "秒杀价必须大于0")
	private BigDecimal seckillPrice;

	@Schema(description = "秒杀库存")
	@NotNull(message = "秒杀库存不能为空")
	@Positive(message = "秒杀库存必须大于0")
	private Integer seckillStock;

	@Schema(description = "每人限购数")
	@Min(value = 1, message = "限购数至少为1")
	private Integer limitPerUser;
}