package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 访问→下单→支付用户漏斗。
 */
@Data
@Schema(description = "访问转化漏斗")
public class TrafficFunnelVO {

	@Schema(description = "访问用户数")
	private Long visitorCount;

	@Schema(description = "下单用户数（按下单时间）")
	private Long createdBuyerCount;

	@Schema(description = "支付用户数（下单 cohort 中已支付）")
	private Long paidBuyerCount;

}
