package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 统一营销活动（二期：4 阶梯价 / 7 船供整船优惠）。
 *
 * <p>规则以受控 JSON 存储：阶梯价 [{minQty,unitPrice}]；
 * 整船优惠 [{minAmount,discountAmount}]。发布后核心规则不可修改，只能暂停或新建版本。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("promotion_activity")
public class PromotionActivity extends Model<PromotionActivity> {

	public static final String TYPE_LADDER_PRICE = "4";

	public static final String TYPE_SHIP_WHOLE_DISCOUNT = "7";

	/** 买赠 */
	public static final String TYPE_GIFT = "5";

	public static final String STATUS_DRAFT = "1";

	public static final String STATUS_PUBLISHED = "2";

	public static final String STATUS_PAUSED = "3";

	public static final String STATUS_ENDED = "4";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 活动名称 */
	private String activityName;

	/** 活动类型：4阶梯价 7船供整船优惠 */
	private String activityType;

	/** 范围类型：1全场 2指定SKU 4指定购买场景 5指定船舶 6指定靠港计划 7指定港口 */
	private String scopeType;

	/** 范围值（SKU/船舶/靠港ID逗号分隔或场景/港口编码） */
	private String scopeValue;

	/** 限定购买场景：1个人 2船供（空不限） */
	private String purchaseScene;

	/** 规则快照（受控JSON） */
	private String rules;

	/** 优先级（大者先） */
	private Integer priority;

	/** 是否可叠加：0否 1是 */
	private String stackable;

	/** 开始时间 */
	private LocalDateTime startTime;

	/** 结束时间 */
	private LocalDateTime endTime;

	/** 状态：1草稿 2已发布 3已暂停 4已结束 */
	private String status;

	/** 发布时间 */
	private LocalDateTime publishTime;

	/** 备注 */
	private String remark;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
