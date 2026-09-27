package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * C端签到记录VO
 *
 * <p>相对管理端的 {@link SignInRecordVO} 去掉了 userId / nickname：
 * C 端查的是自己的记录，回内部 ID 与昵称没有意义。
 *
 * @author 雨滴kian
 */
@Data
public class AppSignInRecordVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "签到日期")
	private LocalDate signDate;

	@Schema(description = "连续签到天数")
	private Integer consecutiveDay;

	@Schema(description = "获得积分")
	private Integer rewardPoint;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	public static AppSignInRecordVO from(SignInRecordVO source) {
		AppSignInRecordVO vo = new AppSignInRecordVO();
		vo.setId(source.getId());
		vo.setSignDate(source.getSignDate());
		vo.setConsecutiveDay(source.getConsecutiveDay());
		vo.setRewardPoint(source.getRewardPoint());
		vo.setCreateTime(source.getCreateTime());
		return vo;
	}

}
