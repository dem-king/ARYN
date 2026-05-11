package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品概览 VO
 */
@Data
@Schema(description = "商品概览数据")
public class ProductOverviewVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "在售商品数量")
	private Integer onSaleCount;

	@Schema(description = "下架商品数量")
	private Integer offShelfCount;

	@Schema(description = "库存预警数量")
	private Integer lowStockCount;

	@Schema(description = "商品总库存")
	private Integer totalStock;

}
