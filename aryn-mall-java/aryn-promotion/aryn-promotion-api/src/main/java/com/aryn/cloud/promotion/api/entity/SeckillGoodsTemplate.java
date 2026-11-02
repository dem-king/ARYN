package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "秒杀排期商品模板")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_goods_template")
public class SeckillGoodsTemplate extends Model<SeckillGoodsTemplate> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "排期模板ID")
	@NotBlank(message = "排期模板ID不能为空")
	private String templateId;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	@Schema(description = "秒杀价")
	@NotNull(message = "秒杀价不能为空")
	@Positive(message = "秒杀价必须大于0")
	private BigDecimal seckillPrice;

	/** 每场计划库存；当天所有场次之和即每日配额 */
	@Schema(description = "每场计划库存（每日配额=各场之和）")
	@NotNull(message = "秒杀库存不能为空")
	@Positive(message = "秒杀库存必须大于0")
	private Integer seckillStock;

	@Schema(description = "每人每场限购数")
	private Integer limitPerUser;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;
}
