package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分销提现单
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销提现单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_withdraw")
public class DistributionWithdraw extends Model<DistributionWithdraw> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "提现单号")
	private String withdrawNo;

	@Schema(description = "用户ID")
	@NotBlank(message = "用户ID不能为空")
	private String userId;

	@Schema(description = "提现金额")
	@NotNull(message = "提现金额不能为空")
	private BigDecimal amount;

	@Schema(description = "状态：0待审核 1已通过 2已拒绝")
	private String status;

	@Schema(description = "收款类型")
	private String accountType;

	@Schema(description = "收款人")
	private String accountName;

	@Schema(description = "收款账号")
	private String accountNo;

	@Schema(description = "拒绝原因")
	private String rejectReason;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "审核时间")
	private LocalDateTime auditTime;

	@Schema(description = "审核人")
	private String auditBy;

	@Schema(description = "线下打款流水号")
	private String payoutNo;

	@Schema(description = "线下打款时间")
	private LocalDateTime payoutTime;

	@Schema(description = "线下打款确认人")
	private String payoutBy;

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
