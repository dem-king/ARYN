package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分销配置
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_config")
public class DistributionConfig extends Model<DistributionConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "配置名称")
	@NotBlank(message = "配置名称不能为空")
	private String configName;

	@Schema(description = "一级佣金比例")
	@NotNull(message = "一级佣金比例不能为空")
	@DecimalMin(value = "0", message = "一级佣金比例不能小于0")
	@DecimalMax(value = "1", message = "一级佣金比例不能大于1")
	private BigDecimal commissionRate;

	@Schema(description = "二级佣金比例")
	@DecimalMin(value = "0", message = "二级佣金比例不能小于0")
	@DecimalMax(value = "1", message = "二级佣金比例不能大于1")
	private BigDecimal commissionRateLevel2;

	@Schema(description = "最低提现金额")
	@NotNull(message = "最低提现金额不能为空")
	@DecimalMin(value = "0", message = "最低提现金额不能小于0")
	private BigDecimal minWithdrawAmount;

	@Schema(description = "结算周期(天)")
	@Min(value = 0, message = "结算周期不能小于0天")
	@Max(value = 365, message = "结算周期不能超过365天")
	private Integer settleCycleDays;

	@Schema(description = "状态：0启用 1禁用")
	@NotBlank(message = "状态不能为空")
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
