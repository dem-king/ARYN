
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单商品DTO
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Data
@Schema(description = "订单商品DTO")
public class CreateOrderSkuReqDTO {

	@Schema(description = "spuId")
	private String spuId;

	@Schema(description = "skuId")
	private String skuId;

	@Schema(description = "购买数量")
	private Integer quantity;

	@Schema(description = "运费（元）")
	private BigDecimal freightPrice;

	@Schema(description = "优惠券优惠金额（元）")
	private BigDecimal couponPrice;

	@Schema(description = "支付金额（总金额-优惠券优惠金额+运费 = 支付金额）")
	private BigDecimal paymentPrice;

	@Schema(description = "规格")
	private String specsInfo;

	@Schema(description = "规格图")
	private String picUrl;

}
