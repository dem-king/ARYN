package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 会员成长值变动记录
 *
 * @author aryn
 */
@Data
@Schema(description = "会员成长值变动记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_growth_log")
public class MemberGrowthLog extends Model<MemberGrowthLog> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "变动值(正加负减)")
	private Integer growthValue;

	@Schema(description = "来源(order/sign_in/review/refund/admin)")
	private String source;

	@Schema(description = "业务ID")
	private String bizId;

	@Schema(description = "变动后成长值")
	private Integer afterValue;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

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

}