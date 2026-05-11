package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品销量排行 VO
 */
@Data
@Schema(description = "商品销量排行")
public class ProductSalesRankVO {

	@Schema(description = "商品ID")
	private String id;

	@Schema(description = "商品名称")
	private String name;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "销量")
	private Integer salesVolume;

}
