package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 储值配置
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "储值配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "recharge_config")
public class RechargeConfig extends Model<RechargeConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "充值金额")
	private BigDecimal rechargeAmount;

	@Schema(description = "赠送金额")
	private BigDecimal giftAmount = BigDecimal.ZERO;

	@Schema(description = "赠送积分")
	private Integer giftPoint = 0;

	@Schema(description = "排序号")
	private Integer sortOrder;

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
