package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单退款率统计 VO
 */
@Data
@Schema(description = "订单退款率统计数据")
public class OrderRefundRateVO {

	@Schema(description = "商品名称")
	private String spuName;

	@Schema(description = "商品图片")
	private String picUrl;

	@Schema(description = "退款率 (退款金额 / 销售金额)")
	private BigDecimal refundRate;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

	@Schema(description = "销售金额")
	private BigDecimal salesAmount;

}
