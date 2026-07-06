
package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 积分兑换记录
 *
 * @author aryn
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

	@Schema(description = "积分商品ID")
	private String pointsGoodsId;

	@Schema(description = "消耗积分")
	private Integer pointsCost;

	@Schema(description = "商品类型：goods-实物商品；coupon-优惠券；gift-赠品；")
	private String type;

	@Schema(description = "状态：pending-待处理；success-成功；failed-失败；")
	private String status;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}