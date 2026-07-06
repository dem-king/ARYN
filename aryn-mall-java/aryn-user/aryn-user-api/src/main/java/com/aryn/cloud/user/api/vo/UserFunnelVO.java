package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户漏斗统计 VO (注册→下单→复购)
 */
@Data
@Schema(description = "用户漏斗统计数据")
public class UserFunnelVO {

	@Schema(description = "注册用户数")
	private Long registerCount;

	@Schema(description = "下单用户数")
	private Long orderCount;

	@Schema(description = "复购用户数 (下单2次及以上)")
	private Long repurchaseCount;

}