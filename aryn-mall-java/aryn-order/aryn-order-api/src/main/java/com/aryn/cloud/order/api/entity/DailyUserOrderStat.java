package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户×日订单汇总。
 */
@Data
@Schema(description = "用户日订单汇总")
@EqualsAndHashCode(callSuper = true)
@TableName("stat_daily_user_order")
public class DailyUserOrderStat extends Model<DailyUserOrderStat> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "统计日期")
	private LocalDate statDate;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "当日支付订单数")
	private Integer orderCount;

	@Schema(description = "当日支付金额")
	private BigDecimal paidAmount;

	@Schema(description = "当日最后一次支付时间")
	private LocalDateTime lastPaymentTime;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}
