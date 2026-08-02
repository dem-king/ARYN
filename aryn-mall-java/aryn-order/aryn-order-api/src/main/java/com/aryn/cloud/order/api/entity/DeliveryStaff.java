
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "配送员")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_staff")
public class DeliveryStaff extends Model<DeliveryStaff> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "关联sys_user后台用户ID")
	private String userId;

	@Schema(description = "配送员姓名")
	private String staffName;

	@Schema(description = "手机号")
	private String staffPhone;

	@Schema(description = "状态：1在线 2忙碌 3离线")
	private String status;

	@Schema(description = "车辆信息")
	private String vehicleInfo;

	@Schema(description = "微信openid")
	private String openid;

	@Schema(description = "租户ID")
	private String tenantId;

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

	@Schema(description = "搜索关键字")
	@TableField(exist = false)
	private String keyword;

}