package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 会员权益
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "会员权益")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_benefit")
public class MemberBenefit extends Model<MemberBenefit> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "权益名称")
	private String benefitName;

	@Schema(description = "权益类型：1-折扣；2-免运费；3-专属优惠券；4-积分倍率")
	private String benefitType;

	@Schema(description = "权益值")
	private String benefitValue;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "状态：0-启用；1-禁用")
	private String status;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
