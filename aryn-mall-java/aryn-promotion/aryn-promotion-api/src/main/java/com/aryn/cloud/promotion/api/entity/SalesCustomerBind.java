package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 客户销售归属
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户销售归属")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_customer_bind")
public class SalesCustomerBind extends Model<SalesCustomerBind> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "归属销售ID")
	private String salesStaffId;

	@Schema(description = "绑定来源：SHARE/QRCODE/ADMIN/TRANSFER/CLAIM")
	private String bindSource;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

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
