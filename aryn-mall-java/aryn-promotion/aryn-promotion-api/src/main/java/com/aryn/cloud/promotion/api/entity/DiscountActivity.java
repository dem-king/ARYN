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

@Data
@Schema(description = "折扣活动")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "discount_activity")
public class DiscountActivity extends Model<DiscountActivity> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动名称")
	@NotBlank(message = "活动名称不能为空")
	private String activityName;

	@Schema(description = "开始时间")
	@NotNull(message = "开始时间不能为空")
	private LocalDateTime startTime;

	@Schema(description = "结束时间")
	@NotNull(message = "结束时间不能为空")
	private LocalDateTime endTime;

	@Schema(description = "折扣类型:1打折 2减价 3固定价")
	@NotNull(message = "折扣类型不能为空")
	private Integer discountType;

	@Schema(description = "折扣值")
	@NotNull(message = "折扣值不能为空")
	private BigDecimal discountValue;

	@Schema(description = "适用范围:1全场 2指定商品")
	private Integer scope;

	/** 活动级限量（仅全场型 scope=1 生效）：NULL = 不限量 */
	private Integer stock;

	/** 活动级已占用数量；stock 为 NULL 时恒为 0 */
	private Integer soldCount;

	@Schema(description = "状态:0未开始 1进行中 2已结束 3已暂停")
	private Integer status;

	/** 活动商品是否可用优惠券：1可用 0不可用（空按类型默认值=可用处理） */
	private String couponStackable;

	/** 活动商品是否可积分抵现：1可抵 0不可抵（空按类型默认值=可抵处理） */
	private String pointsStackable;

	@Schema(description = "活动描述")
	private String description;

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
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "版本号")
	@Version
	private Integer version;

	@Schema(description = "租户ID")
	private String tenantId;
}