package com.aryn.cloud.product.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 补给单导入行匹配请求（订单域 → 商品域，Dubbo）。
 *
 * <p>补给清单是**线下 Excel**，字段只有编码/品名/规格/数量：
 * 商品域负责把它落到 SKU 上，订单域负责落导入记录与写回补给单。
 * 匹配所需的数据（编码映射、品名、规格、库存、上下架）都在商品域，
 * 因此匹配算法放在商品域，订单域只传原文与拿结果。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单导入行匹配请求")
public class ReplenishImportMatchDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "行号（从1开始，回显用）")
	private Integer rowNo;

	@Schema(description = "商品编码原文（IMPA/ISSA/内部编码/条码/供应商编码，自动识别）")
	private String code;

	@Schema(description = "品名原文")
	private String name;

	@Schema(description = "规格原文")
	private String spec;

	@Schema(description = "数量原文（是否合法由订单域按 MOQ/步长判定）")
	private Integer quantity;

}
