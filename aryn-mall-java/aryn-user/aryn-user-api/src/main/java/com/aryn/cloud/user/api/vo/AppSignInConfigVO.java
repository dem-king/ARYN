package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * C端签到奖励配置VO
 *
 * <p>只暴露展示字段：签到配置实体带 createBy/tenantId/delFlag 等内部字段，
 * 不能直接回给 C 端。
 *
 * @author 雨滴kian
 */
@Data
public class AppSignInConfigVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "连续签到天数")
	private Integer consecutiveDay;

	@Schema(description = "奖励积分")
	private Integer rewardPoint;

	@Schema(description = "排序号")
	private Integer sortOrder;

}
