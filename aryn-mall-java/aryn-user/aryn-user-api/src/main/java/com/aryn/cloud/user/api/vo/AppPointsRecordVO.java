package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C端积分记录VO
 *
 * <p>相对管理端的 {@link PointsRecordVO} 去掉了 userId / nickname。
 *
 * @author 雨滴kian
 */
@Data
public class AppPointsRecordVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "变动类型：1-获取；2-消耗")
	private String changeType;

	@Schema(description = "变动积分")
	private Integer changePoint;

	@Schema(description = "变动后余额")
	private Integer balanceAfter;

	@Schema(description = "触发场景")
	private String triggerScene;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	public static AppPointsRecordVO from(PointsRecordVO source) {
		AppPointsRecordVO vo = new AppPointsRecordVO();
		vo.setId(source.getId());
		vo.setChangeType(source.getChangeType());
		vo.setChangePoint(source.getChangePoint());
		vo.setBalanceAfter(source.getBalanceAfter());
		vo.setTriggerScene(source.getTriggerScene());
		vo.setRemark(source.getRemark());
		vo.setCreateTime(source.getCreateTime());
		return vo;
	}

}
