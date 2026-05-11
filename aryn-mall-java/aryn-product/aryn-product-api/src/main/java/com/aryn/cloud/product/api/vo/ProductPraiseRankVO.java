package com.aryn.cloud.product.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品好评榜 VO
 */
@Data
@Schema(description = "商品好评榜数据")
public class ProductPraiseRankVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "好评数")
	private Integer goodAppraiseCount;

	@Schema(description = "好评率")
	private BigDecimal goodAppraiseRate;

}
