package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 商品访问趋势 VO
 */
@Data
@Schema(description = "商品访问趋势数据")
public class ProductVisitTrendVO {

	@Schema(description = "时间点 (小时 或 日期)")
	private String timePoint;

	@Schema(description = "浏览量 (PV)")
	private Integer pv;

	@Schema(description = "访客数 (UV)")
	private Integer uv;

}
