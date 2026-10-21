package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 周期内单用户成交统计，用于渠道归因。
 */
@Data
@Schema(description = "单用户成交统计")
public class ChannelUserStatVO {

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "支付订单数")
	private Integer orderCount;

	@Schema(description = "支付金额")
	private BigDecimal gmv;

}
