package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户消费次数分布 VO
 */
@Data
@Schema(description = "用户消费次数分布数据")
public class UserConsumptionFrequencyVO {

	@Schema(description = "消费 1 次的用户数量")
	private Integer oneTimeCount;

	@Schema(description = "消费 2-3 次的用户数量")
	private Integer twoToThreeTimesCount;

	@Schema(description = "消费 4-5 次的用户数量")
	private Integer fourToFiveTimesCount;

	@Schema(description = "消费 6 次及以上的用户数量")
	private Integer sixPlusTimesCount;

}
