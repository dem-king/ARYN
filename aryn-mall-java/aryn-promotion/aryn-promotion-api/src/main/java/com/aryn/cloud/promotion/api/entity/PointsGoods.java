
package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 积分商品
 *
 * @author aryn
 */
@Data
@Schema(description = "积分商品")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_goods")
public class PointsGoods extends Model<PointsGoods> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "商品名称")
	@NotBlank(message = "商品名称为空")
	private String name;

	@Schema(description = "封面图")
	private String cover;

	@Schema(description = "商品类型：goods-实物商品；coupon-优惠券；gift-赠品；")
	@NotBlank(message = "商品类型为空")
	private String type;

	@Schema(description = "目标ID（优惠券ID或商品SPU ID）")
	private String targetId;

	@Schema(description = "所需积分")
	@NotNull(message = "所需积分不能为空")
	private Integer pointsPrice;

	@Schema(description = "库存")
	@NotNull(message = "库存不能为空")
	private Integer stock;

	@Schema(description = "每人限购数量，0表示不限购")
	private Integer limitPerUser;

	@Schema(description = "活动开始时间")
	private LocalDateTime startTime;

	@Schema(description = "活动结束时间")
	private LocalDateTime endTime;

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

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "版本号")
	@Version
	private Integer version;

}