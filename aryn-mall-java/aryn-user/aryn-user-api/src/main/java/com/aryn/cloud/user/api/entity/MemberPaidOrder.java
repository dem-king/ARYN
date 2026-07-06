package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 付费会员订单
 *
 * @author aryn
 */
@Data
@Schema(description = "付费会员订单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_paid_order")
public class MemberPaidOrder extends Model<MemberPaidOrder> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "会员等级ID")
	private String memberLevelId;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "开通价格")
	private BigDecimal price;

	@Schema(description = "购买月数")
	private Integer duration;

	@Schema(description = "生效时间")
	private LocalDateTime startTime;

	@Schema(description = "到期时间")
	private LocalDateTime endTime;

	@Schema(description = "状态：pending/paid/cancelled/expired")
	private String status;

	@Schema(description = "支付时间")
	private LocalDateTime payTime;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

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

}