package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Schema(description = "折扣商品关联")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "discount_goods")
public class DiscountGoods extends Model<DiscountGoods> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "商品SPU ID")
	@NotBlank(message = "商品SPU不能为空")
	private String spuId;

	@Schema(description = "商品SKU ID")
	@NotBlank(message = "商品SKU不能为空")
	private String skuId;

	/**
	 * 限量库存：NULL = 不限量（存量行保持 NULL，行为与改造前一致）。
	 * 下单占用（sold_count 增加）、取消/退款回补；见 DiscountStockService。
	 */
	private Integer stock;

	/** 已占用数量：仅 stock 非空时维护，stock 为 NULL 时恒为 0 */
	private Integer soldCount;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;
}