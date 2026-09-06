package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "秒杀下单DTO")
public class SeckillOrderDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "场次ID")
	@NotBlank(message = "场次ID不能为空")
	private String sessionId;

	@Schema(description = "秒杀商品ID")
	@NotBlank(message = "秒杀商品ID不能为空")
	private String seckillGoodsId;

	@Schema(description = "SKU ID")
	@NotBlank(message = "SKU ID不能为空")
	private String skuId;

	@Schema(description = "购买数量")
	@NotNull(message = "购买数量不能为空")
	@Min(value = 1, message = "购买数量至少为1")
	@Max(value = 99, message = "单次购买数量不能超过99")
	private Integer quantity;
}