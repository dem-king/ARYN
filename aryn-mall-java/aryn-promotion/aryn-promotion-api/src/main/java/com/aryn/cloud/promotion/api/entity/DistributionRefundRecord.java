package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "分销退款幂等记录")
@TableName("distribution_refund_record")
public class DistributionRefundRecord {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String refundNo;

	private String bizOrderId;

	private BigDecimal refundAmount;

	private BigDecimal refundBaseAmount;

	private String applied;

	private LocalDateTime appliedTime;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	private String tenantId;
}
