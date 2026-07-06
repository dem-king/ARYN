package com.aryn.cloud.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 大屏用户漏斗 VO
 */
@Data
@Schema(description = "大屏用户漏斗数据")
public class VisualUserFunnelVO {

	@Schema(description = "注册用户数")
	private Long registerCount;

	@Schema(description = "下单用户数")
	private Long orderCount;

	@Schema(description = "复购用户数")
	private Long repurchaseCount;

}