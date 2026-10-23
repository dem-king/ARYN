package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 积分抵现上下文：下单/结算时由用户域返回，订单域据此计算可抵扣额度。
 */
@Data
@Schema(description = "积分抵现上下文")
public class PointsDeductContextVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "是否启用积分抵现")
	private boolean enabled;

	@Schema(description = "当前可用积分")
	private Integer availablePoints;

	@Schema(description = "每抵扣 1 元所需积分")
	private Integer pointsPerYuan;

}
