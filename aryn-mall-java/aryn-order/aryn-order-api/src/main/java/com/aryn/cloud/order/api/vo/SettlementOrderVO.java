
package com.aryn.cloud.order.api.vo;

import com.aryn.cloud.order.api.entity.OrderInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "结算订单VO")
public class SettlementOrderVO {

	@Schema(description = "订单金额（元）")
	private BigDecimal salesPrice;

	@Schema(description = "订单总金额（元）")
	private BigDecimal totalPrice;

	@Schema(description = "运费（元）")
	private BigDecimal freightPrice;

	@Schema(description = "优惠券优惠金额（元）")
	private BigDecimal couponPrice;

	@Schema(description = "支付金额（总金额-优惠券优惠金额+运费 = 支付金额）")
	private BigDecimal paymentPrice;

	private List<OrderInfo> orderList;

}
