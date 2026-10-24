package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 积分兑换记录：一次兑换 = 一条记录 = 扣一次积分 + 发一张券。
 *
 * <p>记录 ID 同时是扣积分幂等键（POINTS_EXCHANGE:{记录ID}）与发券来源单号
 * （sourceType=POINTS_EXCHANGE），任一侧重放都只会落到同一条业务事实。
 */
@Data
@Schema(description = "积分兑换记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_exchange_record")
public class PointsExchangeRecord extends Model<PointsExchangeRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "兑换项ID")
	private String exchangeId;

	@Schema(description = "兑换项标题快照")
	private String exchangeTitle;

	@Schema(description = "消耗积分快照")
	private Integer pointsCost;

	@Schema(description = "优惠券模板ID快照")
	private String couponId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
