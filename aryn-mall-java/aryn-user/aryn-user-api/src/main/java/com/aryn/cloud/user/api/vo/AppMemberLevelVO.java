package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * C端会员等级VO
 *
 * <p>只暴露展示字段：等级实体带 createBy/tenantId/delFlag 等内部字段，
 * 不能直接回给 C 端。
 *
 * @author 雨滴kian
 */
@Data
public class AppMemberLevelVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "等级名称")
	private String levelName;

	@Schema(description = "等级图标")
	private String levelIcon;

	@Schema(description = "升级条件类型：1.累计消费金额；2.累计积分")
	private String conditionType;

	@Schema(description = "升级条件值")
	private BigDecimal conditionValue;

	@Schema(description = "排序号")
	private Integer sortOrder;

}
