package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Schema(description = "拼团参与记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "group_buy_member")
public class GroupBuyMember extends Model<GroupBuyMember> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "拼团记录ID")
	@NotNull(message = "拼团记录ID不能为空")
	private String recordId;

	@Schema(description = "拼团活动ID")
	@NotNull(message = "拼团活动ID不能为空")
	private String activityId;

	@Schema(description = "参团用户ID")
	@NotBlank(message = "用户ID不能为空")
	private String userId;

	@Schema(description = "关联订单ID")
	private String orderId;

	@Schema(description = "参团状态:0待付款,1已付款,2已取消")
	private String memberStatus;

	@Schema(description = "是否团长:0否,1是")
	private String isLeader;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	@Version
	private Integer version;

	private String tenantId;
}
