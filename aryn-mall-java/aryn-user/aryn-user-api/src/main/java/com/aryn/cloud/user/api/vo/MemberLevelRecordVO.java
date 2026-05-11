package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会员等级变更记录VO
 *
 * @author 雨滴kian
 */
@Data
public class MemberLevelRecordVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "会员昵称")
	private String nickname;

	@Schema(description = "原等级ID")
	private String oldLevelId;

	@Schema(description = "新等级ID")
	private String newLevelId;

	@Schema(description = "原等级名称")
	private String oldLevelName;

	@Schema(description = "新等级名称")
	private String newLevelName;

	@Schema(description = "变更原因")
	private String changeReason;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
