package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品浏览排行 VO
 */
@Data
@Schema(description = "商品浏览排行数据")
public class ProductVisitRankVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "浏览量 (PV)")
	private Integer pv;

	@Schema(description = "访客数 (UV)")
	private Integer uv;

}
