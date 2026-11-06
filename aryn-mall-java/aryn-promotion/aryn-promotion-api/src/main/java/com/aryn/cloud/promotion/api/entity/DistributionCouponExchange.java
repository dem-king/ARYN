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
 * 分销佣金兑换优惠券记录
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "分销佣金兑换优惠券记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_coupon_exchange")
public class DistributionCouponExchange extends Model<DistributionCouponExchange> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "分销员会员ID")
	private String userId;

	@Schema(description = "分销员昵称")
	private String nickname;

	@Schema(description = "券包ID")
	private String packId;

	@Schema(description = "券包名称")
	private String packName;

	@Schema(description = "券包面额")
	private BigDecimal faceAmount;

	@Schema(description = "消耗佣金")
	private BigDecimal commissionAmount;

	@Schema(description = "发放的优惠券记录ID（逗号分隔）")
	private String couponUserIds;

	@Schema(description = "状态：0发放中 1已发放 2发放失败")
	private String status;

	@Schema(description = "兑换时间")
	private LocalDateTime exchangeTime;

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
