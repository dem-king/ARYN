package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 补给单 Excel 导入报告行。
 *
 * <p>原始 Excel 文本与匹配结果都返回：报告要能解释「为什么这行没匹配上」，
 * 只给结论不给依据时用户只能反复试改文件。
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单Excel导入报告行")
public class SharedCartImportRowVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "行号（从1开始）")
	private Integer rowNo;

	@Schema(description = "Excel 商品编码原文")
	private String rawCode;

	@Schema(description = "Excel 品名原文")
	private String rawName;

	@Schema(description = "Excel 规格原文")
	private String rawSpec;

	@Schema(description = "Excel 数量原文")
	private Integer rawQuantity;

	@Schema(description = "Excel 单位原文")
	private String rawUnit;

	@Schema(description = "Excel 备注原文")
	private String rawRemark;

	@Schema(description = "匹配方式：CODE / NAME / AMBIGUOUS / NONE")
	private String matchType;

	@Schema(description = "匹配到的SKU ID")
	private String matchedSkuId;

	@Schema(description = "匹配到的商品名")
	private String matchedName;

	@Schema(description = "匹配到的规格描述")
	private String matchedSpec;

	@Schema(description = "匹配到的采购单位")
	private String matchedUnit;

	@Schema(description = "匹配时的售价")
	private BigDecimal matchedPrice;

	@Schema(description = "匹配时的库存")
	private Integer matchedStock;

	@Schema(description = "计划采购量")
	private Integer plannedQuantity;

	@Schema(description = "结果：OK/UNMATCHED/SPEC_CHANGED/OVER_STOCK/INVALID_QTY/OFF_SHELF")
	private String resultType;

	@Schema(description = "结果说明")
	private String resultMessage;

	@Schema(description = "用户处置动作")
	private String resolvedAction;

	@Schema(description = "人工补选后的SKU ID")
	private String resolvedSkuId;

	@Schema(description = "调整后的数量")
	private Integer resolvedQuantity;

	@Schema(description = "超库存时的建议调减量（不可行时为 null）")
	private Integer suggestedQuantity;

	@Schema(description = "多规格歧义候选（最多 20 条，供人工补选）")
	private List<com.aryn.cloud.product.api.vo.ReplenishImportMatchVO.Candidate> candidates;

}
