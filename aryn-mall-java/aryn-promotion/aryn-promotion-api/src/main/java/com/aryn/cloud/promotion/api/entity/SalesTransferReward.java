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
 * 客户转交奖励（分销员 → 销售，一次性推荐奖励）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户转交奖励")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_transfer_reward")
public class SalesTransferReward extends Model<SalesTransferReward> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "原分销员会员ID")
	private String distributorUserId;

	@Schema(description = "接收销售ID")
	private String salesStaffId;

	@Schema(description = "触发订单ID（客户首单）")
	private String bizOrderId;

	@Schema(description = "首单实付金额（不含运费）")
	private BigDecimal firstOrderAmount;

	@Schema(description = "奖励比例快照")
	private BigDecimal rewardRate;

	@Schema(description = "奖励金额")
	private BigDecimal rewardAmount;

	@Schema(description = "状态：0待结算 1已入账 2已作废")
	private String status;

	@Schema(description = "入账时间")
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
