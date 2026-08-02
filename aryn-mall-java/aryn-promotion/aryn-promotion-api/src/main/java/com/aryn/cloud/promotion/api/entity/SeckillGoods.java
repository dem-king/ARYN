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
@Schema(description = "秒杀商品")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_goods")
public class SeckillGoods extends Model<SeckillGoods> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "场次ID")
	@NotBlank(message = "场次ID不能为空")
	private String sessionId;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	@Schema(description = "秒杀价")
	@NotNull(message = "秒杀价不能为空")
	private BigDecimal seckillPrice;

	@Schema(description = "秒杀库存")
	@NotNull(message = "秒杀库存不能为空")
	private Integer seckillStock;

	@Schema(description = "每人限购数")
	private Integer limitPerUser;

	@Schema(description = "已售数量")
	private Integer soldCount;

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

	@Schema(description = "租户ID")
	private String tenantId;
}