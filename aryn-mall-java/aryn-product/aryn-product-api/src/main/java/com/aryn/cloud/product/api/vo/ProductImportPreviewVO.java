package com.aryn.cloud.product.api.vo;

import com.aryn.cloud.product.api.entity.ProductImportError;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 商品导入预览结果 VO。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "商品导入预览结果VO")
public class ProductImportPreviewVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "导入任务ID")
	private String jobId;

	@Schema(description = "总行数")
	private Integer totalRows;

	@Schema(description = "可导入行数")
	private Integer successRows;

	@Schema(description = "错误行数")
	private Integer errorRows;

	@Schema(description = "是否可确认导入")
	private Boolean confirmable;

	@Schema(description = "错误明细")
	private List<ProductImportError> errors;

}
