package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Schema(description = "秒杀开抢提醒订阅")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_subscribe")
public class SeckillSubscribe extends Model<SeckillSubscribe> {

	/** 状态：0待发送 1已投递 2已取消 3发送失败 */
	public static final int STATUS_PENDING = 0;

	public static final int STATUS_SENT = 1;

	public static final int STATUS_CANCELLED = 2;

	public static final int STATUS_FAILED = 3;

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	private String activityId;

	@Schema(description = "场次ID")
	private String sessionId;

	@Schema(description = "商城用户ID")
	private String mallUserId;

	@Schema(description = "微信订阅模板ID")
	private String templateId;

	@Schema(description = "状态:0待发送 1已投递 2已取消 3发送失败")
	private Integer status;

	@Schema(description = "失败原因")
	private String failReason;

	@Schema(description = "订阅时间")
	private LocalDateTime subscribeTime;

	@Schema(description = "投递时间")
	private LocalDateTime sendTime;

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
