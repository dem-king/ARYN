
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 配送员商城账号绑定
 *
 * <p>同一商城用户在租户内全历史仅一行，解绑通过 status='0' 保留记录，
 * 重新绑定时复用该行更新归属；员工账号唯一有效绑定由服务端事务内校验保证。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "配送员商城账号绑定")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_account_binding")
public class DeliveryAccountBinding extends Model<DeliveryAccountBinding> {

	/** 绑定状态：有效 */
	public static final String STATUS_BOUND = "1";

	/** 绑定状态：解绑 */
	public static final String STATUS_UNBOUND = "0";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "商城用户ID（user_info.id）")
	private String mallUserId;

	@Schema(description = "员工账号ID（sys_user.id）")
	private String sysUserId;

	@Schema(description = "配送员资料ID（delivery_staff.id）")
	private String deliveryStaffId;

	@Schema(description = "绑定状态：1有效 0解绑")
	private String status;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

	@Schema(description = "解绑时间")
	private LocalDateTime unbindTime;

	@Schema(description = "绑定操作人")
	private String bindBy;

	@Schema(description = "解绑操作人")
	private String unbindBy;

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

}
