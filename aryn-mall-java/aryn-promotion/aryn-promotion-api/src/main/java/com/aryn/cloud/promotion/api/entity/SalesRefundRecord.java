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
 * 销售业绩退款幂等记录
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩退款幂等记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_refund_record")
public class SalesRefundRecord extends Model<SalesRefundRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "退款业务号")
	private String refundNo;

	@Schema(description = "业务订单ID")
	private String bizOrderId;

	@Schema(description = "退款总额（含运费）")
	private BigDecimal refundAmount;

	@Schema(description = "退款提成基数（不含运费）")
	private BigDecimal refundBaseAmount;

	@Schema(description = "是否已应用：0否 1是")
	private String applied;

	@Schema(description = "应用完成时间")
	private LocalDateTime appliedTime;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0显示 1隐藏")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}
