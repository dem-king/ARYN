package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 客户待转交认领（客户已归属分销员，又点击了销售分享链接）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户待转交认领")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_customer_claim")
public class SalesCustomerClaim extends Model<SalesCustomerClaim> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "认领销售ID")
	private String salesStaffId;

	@Schema(description = "来源：SHARE/QRCODE")
	private String source;

	@Schema(description = "状态：0待首单 1已转交 2已失效")
	private String status;

	@Schema(description = "认领时间")
	private LocalDateTime claimTime;

	@Schema(description = "失效时间")
	private LocalDateTime expireTime;

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
