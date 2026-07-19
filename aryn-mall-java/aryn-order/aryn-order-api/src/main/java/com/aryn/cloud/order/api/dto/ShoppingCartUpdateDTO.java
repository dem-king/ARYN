package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "购物车修改DTO")
public class ShoppingCartUpdateDTO {

	@NotBlank(message = "购物车ID不能为空")
	@Schema(description = "购物车ID")
	private String id;

	@Schema(description = "切换后的SKU ID，不传时只修改数量")
	private String skuId;

	@NotNull(message = "购买数量不能为空")
	@Min(value = 1, message = "购买数量必须大于0")
	@Schema(description = "购买数量")
	private Integer quantity;

}
