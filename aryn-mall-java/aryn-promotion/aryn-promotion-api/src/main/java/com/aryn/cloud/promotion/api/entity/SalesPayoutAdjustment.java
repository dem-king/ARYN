package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已发放业绩单退款产生的负数结转（结转到下月发放台账）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "提成负数结转")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_payout_adjustment")
public class SalesPayoutAdjustment extends Model<SalesPayoutAdjustment> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "销售ID")
	private String salesStaffId;

	@Schema(description = "销售姓名")
	private String staffName;

	@Schema(description = "业务订单ID")
	private String bizOrderId;

	@Schema(description = "退款业务号")
	private String refundNo;

	@Schema(description = "调整金额（负数）")
	private BigDecimal amount;

	@Schema(description = "状态：0待结转 1已结转")
	private String status;

	@Schema(description = "结转批次")
	private String payoutId;

	@Schema(description = "备注")
	private String remark;

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
	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "版本号")
	@Version
	private Integer version;

}
