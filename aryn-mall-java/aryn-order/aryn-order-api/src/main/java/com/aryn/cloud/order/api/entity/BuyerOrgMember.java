package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 采购主体成员。
 *
 * <p>{@code (tenant_id, buyer_org_id, user_id)} 唯一：退出/被移除只置
 * {@code status=0} 并写 {@code leave_time}，不物理删除、也不置 del_flag，
 * 这样同一用户再次加入时复用原行，不会撞唯一键。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体成员")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("buyer_org_member")
public class BuyerOrgMember extends Model<BuyerOrgMember> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "采购主体ID")
	private String buyerOrgId;

	@Schema(description = "商城用户ID")
	private String userId;

	@Schema(description = "角色：1厂长/管理员 2采购员 3业务员 4查看者")
	private String memberRole;

	@Schema(description = "1可加购/改数/建车")
	private String canEdit;

	@Schema(description = "1可确认提交")
	private String canConfirm;

	@Schema(description = "状态：1正常 0已移除")
	private String status;

	@Schema(description = "绑定来源，口径同 buyer_org.bind_source")
	private String bindSource;

	@Schema(description = "关联申请单ID")
	private String applyId;

	@Schema(description = "加入时间")
	private LocalDateTime joinTime;

	@Schema(description = "退出/移除时间")
	private LocalDateTime leaveTime;

	@Schema(description = "备注")
	private String remark;

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
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}
