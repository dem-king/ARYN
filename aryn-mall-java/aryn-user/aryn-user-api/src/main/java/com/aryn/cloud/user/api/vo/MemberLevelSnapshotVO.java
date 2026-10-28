package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员等级快照：跨域 Dubbo 传输的等级最小信息，供促销域会员价资格按 sortOrder 比较等级高低。
 *
 * <p>只回判定所需字段，不暴露 MemberLevel 实体的 createBy/tenantId/delFlag 等内部字段。
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "会员等级快照")
public class MemberLevelSnapshotVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "会员等级ID")
	private String levelId;

	@Schema(description = "会员等级名称")
	private String levelName;

	@Schema(description = "等级排序号，越大等级越高（会员价资格按此比较）")
	private Integer sortOrder;

}
