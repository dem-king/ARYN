package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 配送资格操作（可靠授权/回收记录）
 *
 * <p>订单域本地事务只保证配送员资料与绑定的一致性，UPMS 角色授予/回收是远程操作，
 * 无法随本地事务回滚。本表作为 outbox：本地事务提交前写入待处理操作，
 * 提交后同步执行一次，失败由定时任务按指数退避重试，直到最终成功。
 *
 * @author aryn
 * @since 2026/9/6
 */
@Data
@Schema(description = "配送资格操作")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "delivery_qualification_operation")
public class DeliveryQualificationOperation extends Model<DeliveryQualificationOperation> {

	/** 操作类型：授予角色 */
	public static final String OPERATION_GRANT = "GRANT";

	/** 操作类型：回收角色 */
	public static final String OPERATION_REVOKE = "REVOKE";

	/** 状态：待处理（含等待重试） */
	public static final String STATUS_PENDING = "PENDING";

	/** 状态：已完成 */
	public static final String STATUS_DONE = "DONE";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "配送员资料ID（delivery_staff.id）")
	private String staffId;

	@Schema(description = "员工账号ID（sys_user.id）")
	private String sysUserId;

	@Schema(description = "角色编码")
	private String roleCode;

	@Schema(description = "操作类型：GRANT授予 REVOKE回收")
	private String operation;

	@Schema(description = "状态：PENDING待处理 DONE已完成")
	private String status;

	@Schema(description = "幂等键（operation + sysUserId + roleCode，租户内唯一）")
	private String idempotentKey;

	@Schema(description = "已执行次数（含失败重试）")
	private Integer retryCount;

	@Schema(description = "最近一次失败原因")
	private String lastError;

	@Schema(description = "下次重试时间")
	private LocalDateTime nextRetryTime;

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
