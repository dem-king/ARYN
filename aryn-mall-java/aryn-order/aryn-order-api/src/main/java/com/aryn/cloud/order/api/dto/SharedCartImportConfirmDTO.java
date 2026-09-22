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
 * 补给单 Excel 导入确认 DTO（确认人/发起人操作）。
 *
 * <p>客户端只回传「行号 → 处置动作」，**不传行内容**：
 * 数量与 SKU 都以服务端已落库的解析行为准，确认时重新匹配校验，
 * 避免客户端篡改数量、篡改 SKU 或塞入未解析的行。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单Excel导入确认DTO")
public class SharedCartImportConfirmDTO implements Serializable {

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
