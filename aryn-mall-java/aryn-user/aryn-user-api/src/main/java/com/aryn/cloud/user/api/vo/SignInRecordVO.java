package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 签到记录VO
 *
 * @author 雨滴kian
 */
@Data
public class SignInRecordVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "会员昵称")
	private String nickname;

	@Schema(description = "签到日期")
	private LocalDate signDate;

	@Schema(description = "连续签到天数")
	private Integer consecutiveDay;

	@Schema(description = "获得积分")
	private Integer rewardPoint;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
