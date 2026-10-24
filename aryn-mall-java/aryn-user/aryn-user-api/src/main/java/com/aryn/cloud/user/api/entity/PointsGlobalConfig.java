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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分全局规则配置（每租户一行，缺省由代码兜底）。
 *
 * <p>承载积分风控四参数：有效期天数、单笔抵扣上限比例、最低使用门槛、单笔抵扣金额封顶。
 * 与按场景配置的 {@link PointsConfig}（发放/抵现汇率）互补。
 */
@Data
@Schema(description = "积分全局规则配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_global_config")
public class PointsGlobalConfig extends Model<PointsGlobalConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "积分有效期（天），0=永不过期")
	private Integer validityDays;

	@Schema(description = "单笔抵扣上限比例（百分数，如 20.00 表示 20%）")
	private BigDecimal deductMaxRatio;

	@Schema(description = "最低使用门槛（积分可抵基数满 X 元可用，0=无门槛）")
	private BigDecimal minUseAmount;

	@Schema(description = "单笔抵扣金额封顶（元，0=不封顶）")
	private BigDecimal maxDeductAmount;

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
