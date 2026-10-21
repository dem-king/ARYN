package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 渠道成贡献。
 */
@Data
@Schema(description = "渠道贡献")
public class ChannelContributionVO {

	@Schema(description = "来源编码")
	private String source;

	@Schema(description = "来源名称")
	private String sourceName;

	@Schema(description = "买家数")
	private Long buyerCount;

	@Schema(description = "支付订单数")
	private Long orderCount;

	@Schema(description = "支付金额")
	private BigDecimal gmv;

}
