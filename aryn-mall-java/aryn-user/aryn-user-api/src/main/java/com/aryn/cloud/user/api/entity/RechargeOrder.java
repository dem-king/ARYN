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
 * 充值订单
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "充值订单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "recharge_order")
public class RechargeOrder extends Model<RechargeOrder> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "充值配置ID")
	private String rechargeConfigId;

	@Schema(description = "充值金额")
	private BigDecimal rechargeAmount;

	@Schema(description = "赠送金额")
	private BigDecimal giftAmount;

	@Schema(description = "赠送积分")
	private Integer giftPoint;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "支付状态：0-待支付；1-已支付；2-已取消")
	private String payStatus;

	@Schema(description = "支付时间")
	private LocalDateTime payTime;

	@Schema(description = "支付订单号")
	private String payOrderNo;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
