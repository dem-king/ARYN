package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品生命周期概览（基于累计销量与库存）。
 */
@Data
@Schema(description = "商品生命周期概览")
public class ProductLifecycleVO {

	@Schema(description = "近 30 天新增商品")
	private Long newCount;

	@Schema(description = "动销商品（累计销量 > 0）")
	private Long activeCount;

	@Schema(description = "滞销商品（30 天前创建且累计销量为 0）")
	private Long slowMovingCount;

	@Schema(description = "缺货商品（库存 <= 0）")
	private Long outOfStockCount;

}
