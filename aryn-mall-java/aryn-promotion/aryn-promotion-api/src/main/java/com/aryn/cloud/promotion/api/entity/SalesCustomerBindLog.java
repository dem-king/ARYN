package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 客户换绑日志（只追加，不删除）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户换绑日志")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_customer_bind_log")
public class SalesCustomerBindLog extends Model<SalesCustomerBindLog> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "原销售ID（可空）")
	private String oldSalesStaffId;

	@Schema(description = "原销售姓名")
	private String oldSalesStaffName;

	@Schema(description = "新销售ID")
	private String newSalesStaffId;

	@Schema(description = "新销售姓名")
	private String newSalesStaffName;

	@Schema(description = "变更来源：SHARE/QRCODE/ADMIN/TRANSFER/CLAIM")
	private String bindSource;

	@Schema(description = "变更原因")
	private String reason;

	@Schema(description = "操作人ID")
	private String operatorId;

	@Schema(description = "操作人姓名")
	private String operatorName;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

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
