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

@Data
@Schema(description = "秒杀订单记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_order")
public class SeckillOrder extends Model<SeckillOrder> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "场次ID")
	@NotBlank(message = "场次ID不能为空")
	private String sessionId;

	@Schema(description = "秒杀商品ID")
	@NotBlank(message = "秒杀商品ID不能为空")
	private String seckillGoodsId;

	@Schema(description = "订单ID")
	@NotBlank(message = "订单ID不能为空")
	private String orderId;

	@Schema(description = "用户ID")
	@NotBlank(message = "用户ID不能为空")
	private String userId;

	@Schema(description = "SKU ID")
	@NotBlank(message = "SKU ID不能为空")
	private String skuId;

	@Schema(description = "购买数量")
	@NotNull(message = "购买数量不能为空")
	private Integer quantity;

	@Schema(description = "状态:0未支付 1已支付 2已取消 3已超时")
	private Integer status;

	@Schema(description = "秒杀单价(下单时快照)")
	private java.math.BigDecimal seckillPrice;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "支付/状态变更时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;
}