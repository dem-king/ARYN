package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售业绩全局配置（每租户单行）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩全局配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_config")
public class SalesConfig extends Model<SalesConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "全局默认提成比例")
	private BigDecimal defaultCommissionRate;

	@Schema(description = "客户转交奖励比例（0=不启用）")
	private BigDecimal referralRewardRate;

	@Schema(description = "每月台账生成日（1-28）")
	private Integer monthlyCutoffDay;

	@Schema(description = "退款风险观察期（天）")
	private Integer refundRiskDays;

	@Schema(description = "认领有效期（天）")
	private Integer claimExpireDays;

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
