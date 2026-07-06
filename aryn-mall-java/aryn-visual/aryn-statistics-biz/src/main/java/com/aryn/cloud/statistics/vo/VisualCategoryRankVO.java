package com.aryn.cloud.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 大屏品类排行 VO
 */
@Data
@Schema(description = "大屏品类排行数据")
public class VisualCategoryRankVO {

	@Schema(description = "品类名称")
	private String categoryName;

	@Schema(description = "销量")
	private Integer salesCount;

	@Schema(description = "销售金额")
	private BigDecimal salesAmount;

}