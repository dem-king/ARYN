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
 * 分销佣金兑换券包配置
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "分销佣金兑换券包")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_coupon_pack")
public class DistributionCouponPack extends Model<DistributionCouponPack> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "券包名称")
	private String packName;

	@Schema(description = "面额")
	private BigDecimal faceAmount;

	@Schema(description = "使用门槛")
	private BigDecimal thresholdAmount;

	@Schema(description = "有效期（天）")
	private Integer validDays;

	@Schema(description = "消耗佣金")
	private BigDecimal requiredCommission;

	@Schema(description = "库存（NULL=不限）")
	private Integer stock;

	@Schema(description = "状态：0上架 1下架")
	private String status;

	@Schema(description = "排序")
	private Integer sort;

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

	@Schema(description = "版本号")
	@Version
	private Integer version;

}
