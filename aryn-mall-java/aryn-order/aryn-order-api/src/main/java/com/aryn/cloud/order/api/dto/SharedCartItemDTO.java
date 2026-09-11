package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 共享购物车明细 DTO（添加/修改成员自己的明细）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "共享购物车明细DTO")
public class SharedCartItemDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "明细ID（修改时必填）")
	private String itemId;

	@Schema(description = "商品SPU ID")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "SKU ID不能为空")
	private String skuId;

	@Schema(description = "申请数量（采购单位）")
	@NotNull(message = "申请数量不能为空")
	@Min(value = 1, message = "申请数量必须大于0")
	@Max(value = 999999, message = "申请数量过大")
	private Integer requestedQuantity;

	@Schema(description = "成员备注")
	private String memberRemark;

}
