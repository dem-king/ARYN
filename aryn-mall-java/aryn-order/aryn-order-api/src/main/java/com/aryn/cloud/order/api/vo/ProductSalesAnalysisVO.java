package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品销售能力分析 VO
 */
@Data
@Schema(description = "商品销售能力分析数据")
public class ProductSalesAnalysisVO {

	@Schema(description = "动销商品数 (有销量的商品数量)")
	private Integer activeProductCount;

	@Schema(description = "销售件数")
	private Integer salesCount;

	@Schema(description = "销售额")
	private BigDecimal salesAmount;

	@Schema(description = "客单价 (销售额 / 订单数)")
	private BigDecimal averageTicketSize;

	@Schema(description = "动销率 (动销商品数 / 在售商品数)")
	private BigDecimal activeProductRate;

	@Schema(description = "订单数 (内部使用)")
	private Integer orderCount;

}
