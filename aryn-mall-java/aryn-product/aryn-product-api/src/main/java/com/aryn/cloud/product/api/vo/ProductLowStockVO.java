package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 库存预警商品 VO
 */
@Data
@Schema(description = "库存预警商品数据")
public class ProductLowStockVO {

	@Schema(description = "商品ID")
	private String id;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "剩余库存")
	private Integer stock;

}
