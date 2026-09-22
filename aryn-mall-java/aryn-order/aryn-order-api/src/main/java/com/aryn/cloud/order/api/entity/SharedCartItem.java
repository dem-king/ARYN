package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 共享购物车明细。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shared_cart_item")
public class SharedCartItem extends Model<SharedCartItem> {

	public static final String ITEM_PENDING = "1";

	public static final String ITEM_CONFIRMED = "2";

	public static final String ITEM_REMOVED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 共享购物车ID */
	private String cartId;

	/** 添加成员用户ID（订单明细保留贡献者来源） */
	private String userId;

	/** 商品SPU ID */
	private String spuId;

	/** 商品SKU ID */
	private String skuId;

	/** 成员申请数量（采购单位） */
	private Integer requestedQuantity;

	/** 确认人核定数量（采购单位，确认时填写） */
	private Integer approvedQuantity;

	/**
	 * 计划采购量（采购单位）。
	 *
	 * <p>NULL 表示未设计划；此时**不做进度计算**，也不能回落成
	 * {@code requestedQuantity} 假造进度 —— 后者是"成员报的需求量"，
	 * 与"本次计划采购多少"不是一回事。
	 */
	private Integer plannedQuantity;

	/**
	 * 已采量（采购单位）。
	 *
	 * <p>与 {@code approvedQuantity} 的分工：核定数量是确认人提交整船订单时
	 * 一次性写入的；已采量是收集期间由采购/运营逐步回填的执行进度，
	 * 因此收集阶段就能展示「已采 N 项」。
	 */
	private Integer fulfilledQuantity;

	/** 成员备注 */
	private String memberRemark;

	/** 明细状态：1待确认 2已确认 3已移除 */
	private String status;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
