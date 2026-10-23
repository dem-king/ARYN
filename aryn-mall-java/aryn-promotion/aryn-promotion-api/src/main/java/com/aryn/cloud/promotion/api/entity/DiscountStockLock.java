package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 折扣商品库存占用明细。
 *
 * <p>一笔订单对一个折扣 SKU 一行，唯一键 (tenant_id, order_id, sku_id) 保证
 * 重复调用幂等（Dubbo 重试、用户重复提交都不会重复扣减）。
 * 释放时置 status=0 并记录原因，保留审计痕迹；库存回补在
 * {@link com.aryn.cloud.promotion.service.DiscountStockService#release} 中完成。
 */
@Data
@Schema(description = "折扣商品库存占用")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "discount_stock_lock")
public class DiscountStockLock extends Model<DiscountStockLock> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "商品SKU ID")
	private String skuId;

	@Schema(description = "订单ID")
	private String orderId;

	@Schema(description = "订单明细ID；退款按明细精确回补")
	private String orderItemId;

	@Schema(description = "占用数量")
	private Integer quantity;

	@Schema(description = "1占用 0已释放")
	private String status;

	@Schema(description = "释放原因")
	private String releaseReason;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@Schema(description = "逻辑删除:0正常 1删除")
	private String delFlag;

	/** 占用中 */
	public static final String STATUS_LOCKED = "1";

	/** 已释放 */
	public static final String STATUS_RELEASED = "0";
}