package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 权益等级关联
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "权益等级关联")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_benefit_level_rel")
public class MemberBenefitLevelRel extends Model<MemberBenefitLevelRel> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "权益ID")
	private String benefitId;

	@Schema(description = "等级ID")
	private String levelId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
