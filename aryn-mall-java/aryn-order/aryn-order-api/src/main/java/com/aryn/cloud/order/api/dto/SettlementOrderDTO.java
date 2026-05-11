
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "结算订单DTO")
public class SettlementOrderDTO {

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "配送方式：1.普通快递；2.上门自提")
	private String deliveryWay;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付")
	private String paymentType;

	@Schema(description = "交易类型")
	private String tradeType;

	@Schema(description = "用户收货地址")
	private String userAddressId;

	@Schema(description = "订单创建方式：1.购物车下单；2.普通购买下单")
	private String createWay;

	@Schema(description = "用户优惠券id")
	private String couponUserId;

	@Schema(description = "订单商品sku集合")
	private List<CreateOrderSkuReqDTO> skuReqList;

}
