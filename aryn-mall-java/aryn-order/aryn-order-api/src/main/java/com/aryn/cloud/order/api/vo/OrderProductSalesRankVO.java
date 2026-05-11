package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单商品销量排行 VO
 */
@Data
@Schema(description = "订单商品销量排行数据")
public class OrderProductSalesRankVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "销量")
	private Integer salesCount;

	@Schema(description = "销售金额")
	private BigDecimal salesAmount;

}
