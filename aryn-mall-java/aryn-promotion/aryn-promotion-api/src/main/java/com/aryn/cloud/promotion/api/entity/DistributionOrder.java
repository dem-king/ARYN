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
 * 分销订单
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销订单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_order")
public class DistributionOrder extends Model<DistributionOrder> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "业务订单ID")
	@NotBlank(message = "业务订单ID不能为空")
	private String bizOrderId;

	@Schema(description = "买家用户ID")
	@NotBlank(message = "买家用户ID不能为空")
	private String buyerUserId;

	@Schema(description = "分销员用户ID")
	@NotBlank(message = "分销员用户ID不能为空")
	private String distributorUserId;

	@Schema(description = "订单金额")
	@NotNull(message = "订单金额不能为空")
	private BigDecimal orderAmount;

	@Schema(description = "佣金金额")
	@NotNull(message = "佣金金额不能为空")
	private BigDecimal commissionAmount;

	@Schema(description = "佣金层级：1一级 2二级")
	private Integer commissionLevel;

	@Schema(description = "状态：0待结算 1已结算 2已退款")
	private String status;

	@Schema(description = "结算时间")
	private LocalDateTime settleTime;

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
