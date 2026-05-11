
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
 * 分销佣金流水
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销佣金流水")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_commission_flow")
public class DistributionCommissionFlow extends Model<DistributionCommissionFlow> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	@NotBlank(message = "用户ID不能为空")
	private String userId;

	@Schema(description = "关联业务单号")
	private String bizOrderId;

	@Schema(description = "流水类型：INCOME/EXPENSE")
	@NotBlank(message = "流水类型不能为空")
	private String flowType;

	@Schema(description = "变动金额")
	@NotNull(message = "变动金额不能为空")
	private BigDecimal amount;

	@Schema(description = "变动后余额")
	@NotNull(message = "变动后余额不能为空")
	private BigDecimal balanceAfter;

	@Schema(description = "备注")
	private String remark;

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

}
