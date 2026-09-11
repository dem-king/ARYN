package com.aryn.cloud.promotion.api.entity;

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
 * 营销活动锁定（preview→reserve→confirm→release，按订单+活动幂等）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotion_lock")
public class PromotionLock extends Model<PromotionLock> {

	public static final String STATUS_LOCKED = "1";

	public static final String STATUS_CONFIRMED = "2";

	public static final String STATUS_RELEASED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 订单ID */
	private String orderId;

	/** 订单号冗余 */
	private String orderNo;

	/** 活动ID */
	private String activityId;

	/** 活动类型快照 */
	private String activityType;

	/** 活动名称快照 */
	private String activityName;

	/** 锁定优惠金额 */
	private BigDecimal discountAmount;

	/** 规则快照（受控JSON） */
	private String ruleSnapshot;

	/** 状态：1已锁定 2已确认 3已释放 */
	private String status;

	/** 释放原因：CANCEL/TIMEOUT/REFUND */
	private String releaseReason;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
