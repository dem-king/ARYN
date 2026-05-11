package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户消费金额分层 VO
 */
@Data
@Schema(description = "用户消费金额分层数据")
public class UserConsumptionAmountVO {

	@Schema(description = "消费金额 0-100 元的用户数量")
	private Integer amount0To100Count;

	@Schema(description = "消费金额 100-500 元的用户数量")
	private Integer amount100To500Count;

	@Schema(description = "消费金额 500-2000 元的用户数量")
	private Integer amount500To2000Count;

	@Schema(description = "消费金额 2000 元以上的用户数量")
	private Integer amount2000PlusCount;

}
