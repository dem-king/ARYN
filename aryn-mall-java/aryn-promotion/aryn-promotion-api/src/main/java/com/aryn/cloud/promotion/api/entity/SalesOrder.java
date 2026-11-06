package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售业绩单
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩单")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_order")
public class SalesOrder extends Model<SalesOrder> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "业务订单ID")
	@NotBlank(message = "业务订单ID不能为空")
	private String bizOrderId;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "买家用户ID")
	@NotBlank(message = "买家用户ID不能为空")
	private String buyerUserId;

	@Schema(description = "归属销售ID")
	private String salesStaffId;

	@Schema(description = "归属销售姓名")
	private String staffName;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "实付金额（含运费）")
	private BigDecimal paymentAmount;

	@Schema(description = "运费")
	private BigDecimal freightAmount;

	@Schema(description = "提成基数（实付不含运费）")
	@NotNull(message = "提成基数不能为空")
	private BigDecimal commissionBase;

	@Schema(description = "提成比例快照")
	private BigDecimal commissionRate;

	@Schema(description = "提成金额")
	private BigDecimal commissionAmount;

	@Schema(description = "累计退款基数")
	private BigDecimal refundedBase;

	@Schema(description = "累计扣回提成")
	private BigDecimal refundedCommission;

	@Schema(description = "状态：0待确认 1可发放 2已发放 3已全额退款")
	private String status;

	@Schema(description = "支付时间")
	private LocalDateTime payTime;

	@Schema(description = "计划可发放时间（退款风险观察期止）")
	private LocalDateTime settleAt;

	@Schema(description = "实际可发放/发放时间")
	private LocalDateTime settleTime;

	@Schema(description = "所属发放批次")
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
