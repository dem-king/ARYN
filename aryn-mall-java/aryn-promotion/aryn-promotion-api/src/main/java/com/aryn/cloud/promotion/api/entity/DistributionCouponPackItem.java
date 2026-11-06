package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 券包关联的优惠券模板
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "券包关联优惠券模板")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("distribution_coupon_pack_item")
public class DistributionCouponPackItem extends Model<DistributionCouponPackItem> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "券包ID")
	private String packId;

	@Schema(description = "优惠券模板ID")
	private String couponInfoId;

	@Schema(description = "数量")
	private Integer quantity;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}
