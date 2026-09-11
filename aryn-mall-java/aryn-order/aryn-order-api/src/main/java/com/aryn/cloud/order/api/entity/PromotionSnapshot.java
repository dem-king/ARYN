package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单营销快照（历史订单金额不随活动配置变化）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotion_snapshot")
public class PromotionSnapshot extends Model<PromotionSnapshot> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 订单ID */
	private String orderId;

	/** 订单明细ID（整单优惠为空） */
	private String orderItemId;

	/** 活动ID */
	private String activityId;

	/** 活动类型：1优惠券 2满减 3折扣 4阶梯价 5买赠 6首单/复购 7船供整船优惠 */
	private String activityType;

	/** 活动名称快照 */
	private String promotionName;

	/** 活动规则快照（受控JSON） */
	private String ruleSnapshot;

	/** 优惠金额（元） */
	private BigDecimal discountAmount;

	/** 赠品明细（受控JSON） */
	private String giftItems;

	/** 适用订单明细ID集合 */
	private String appliedItemIds;

	/** 购买场景快照：1海员个人购买 2船供采购 */
	private String purchaseScene;

	/** 优惠前金额（元） */
	private BigDecimal priceBefore;

	/** 优惠后金额（元） */
	private BigDecimal priceAfter;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
