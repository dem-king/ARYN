package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C端用户积分信息VO
 *
 * @author 雨滴kian
 */
@Data
public class UserPointsInfoVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "积分余额")
	private Integer point;

	@Schema(description = "储值余额")
	private BigDecimal balance;

	@Schema(description = "等级名称")
	private String levelName;

	@Schema(description = "30 天内即将过期积分（按剩余可用口径汇总）")
	private Integer expiringPoints;

	@Schema(description = "最近一次过期时间（30 天窗口内最早到期批次）")
	private LocalDateTime nearestExpireTime;

}
