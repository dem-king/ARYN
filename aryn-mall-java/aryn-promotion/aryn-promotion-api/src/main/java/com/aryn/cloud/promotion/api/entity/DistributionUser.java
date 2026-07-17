package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分销用户
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销用户")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_user")
public class DistributionUser extends Model<DistributionUser> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	@NotBlank(message = "用户ID不能为空")
	private String userId;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "用户头像")
	private String avatar;

	@Schema(description = "邀请人ID")
	private String inviterUserId;

	@Schema(description = "累计佣金")
	private BigDecimal totalCommission;

	@Schema(description = "可提现佣金")
	private BigDecimal availableCommission;

	@Schema(description = "待结算佣金")
	private BigDecimal pendingCommission;

	@Schema(description = "已提现佣金")
	private BigDecimal withdrawnCommission;

	@Schema(description = "冻结佣金（提现申请中）")
	private BigDecimal frozenCommission;

	@Schema(description = "退款产生的佣金欠款")
	private BigDecimal commissionDebt;

	@Schema(description = "下级人数")
	private Integer subordinateCount;

	@Schema(description = "状态：0启用 1禁用")
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
	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "版本号")
	@Version
	private Integer version;

}
