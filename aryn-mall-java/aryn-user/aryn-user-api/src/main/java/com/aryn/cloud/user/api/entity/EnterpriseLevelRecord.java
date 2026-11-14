package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 企业等级变更记录。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业等级变更记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_level_record")
public class EnterpriseLevelRecord extends Model<EnterpriseLevelRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "原等级")
	private String oldLevelId;

	@Schema(description = "新等级")
	private String newLevelId;

	@Schema(description = "变更原因")
	private String changeReason;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}
