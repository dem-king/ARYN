package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售员（公司员工销售）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售员")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("sales_staff")
public class SalesStaff extends Model<SalesStaff> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "后台员工账号")
	@NotBlank(message = "员工账号不能为空")
	private String sysUserId;

	@Schema(description = "销售姓名")
	@NotBlank(message = "销售姓名不能为空")
	private String staffName;

	@Schema(description = "手机号")
	private String phone;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "部门名称")
	private String deptName;

	@Schema(description = "销售本人的小程序会员ID")
	@NotBlank(message = "销售小程序会员ID不能为空")
	private String memberUserId;

	@Schema(description = "个人提成比例（0-1），为空取全局默认比例")
	private BigDecimal commissionRate;

	@Schema(description = "状态：0在职 1离职")
	private String status;

	@Schema(description = "离职时间")
	private LocalDateTime leaveTime;

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
