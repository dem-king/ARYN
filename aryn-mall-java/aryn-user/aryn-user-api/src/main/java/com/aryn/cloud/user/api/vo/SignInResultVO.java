package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C端签到结果VO
 *
 * @author 雨滴kian
 */
@Data
public class SignInResultVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "连续签到天数")
	private Integer consecutiveDay;

	@Schema(description = "获得积分")
	private Integer rewardPoint;

}
