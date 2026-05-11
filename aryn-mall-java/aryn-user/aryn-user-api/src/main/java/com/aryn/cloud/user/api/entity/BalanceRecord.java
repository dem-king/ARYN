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
 * 余额变动记录
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "余额变动记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "balance_record")
public class BalanceRecord extends Model<BalanceRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "变动类型：1-充值；2-消费；3-调整")
	private String changeType;

	@Schema(description = "变动金额")
	private BigDecimal changeAmount;

	@Schema(description = "变动后余额")
	private BigDecimal balanceAfter;

	@Schema(description = "触发场景")
	private String triggerScene;

	@Schema(description = "备注")
	private String remark;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
