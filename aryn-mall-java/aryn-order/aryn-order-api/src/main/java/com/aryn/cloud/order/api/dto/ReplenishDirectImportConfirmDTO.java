package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 清单直下单导入确认 DTO。
 *
 * <p>与 {@link SharedCartImportConfirmDTO} 同一防篡改契约：客户端只回传
 * 「行号 → 处置动作」，**不传行内容**——数量与 SKU 都以服务端已落库的
 * 解析行为准，确认时重新匹配校验。无接龙来源，故无 personName。
 *
 * @author aryn
 * @since 2026/11/7
 */
@Data
@Schema(description = "清单直下单导入确认DTO")
public class ReplenishDirectImportConfirmDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "行处置列表；未列出的行按「跳过」处理")
	@Valid
	private List<RowAction> rows;

	@Schema(description = "行处置")
	@Data
	public static class RowAction implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "行号（从1开始，与报告一致）")
		@NotNull(message = "行号不能为空")
		@Min(value = 1, message = "行号不合法")
		private Integer rowNo;

		@Schema(description = "处置动作：ACCEPT_SPEC确认新规格 / ADJUST_QTY调整数量 / "
				+ "REPLACE_SKU人工补选 / SKIP跳过")
		@NotBlank(message = "处置动作不能为空")
		private String action;

		@Schema(description = "人工补选后的SKU ID（action=REPLACE_SKU 时必填）")
		private String skuId;

		@Schema(description = "调整后的数量（action=ADJUST_QTY 时必填）")
		@Min(value = 1, message = "数量必须大于0")
		@Max(value = 999999, message = "数量过大")
		private Integer quantity;

	}

}
