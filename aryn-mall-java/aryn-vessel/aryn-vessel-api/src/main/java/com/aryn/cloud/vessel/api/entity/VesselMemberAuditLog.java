package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 船舶成员关系变更审计（{@code vessel_member_audit_log}）。
 *
 * <p><b>为什么单独建表而不是靠 {@code log.info}</b>：审核通过、解绑此前只有一行日志，
 * 误绑事故追查时连「谁在什么时候把谁绑到哪条船、理由是什么」都答不上来；
 * 日志还会随滚动清理消失。成员关系是数据权限基线，变更必须落成可查询的事实。
 *
 * <p><b>追加写、不提供业务更新</b>：审计行的价值在于不可篡改，因此不暴露 update 入口；
 * 逻辑删除字段继承自 {@link AbstractVesselEntity} 只为与租户/逻辑删除拦截器口径一致，
 * 业务代码不应删除审计记录。
 *
 * @author aryn
 * @since 2026/10/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_member_audit_log")
public class VesselMemberAuditLog extends AbstractVesselEntity {

	/** 动作：首次建立成员关系 */
	public static final String ACTION_BIND = "BIND";

	/** 动作：复活被移除的历史成员行 */
	public static final String ACTION_REBIND = "REBIND";

	/** 动作：角色变更（成员已在船上，本次改了角色/权限位） */
	public static final String ACTION_ROLE_CHANGE = "ROLE_CHANGE";

	/** 动作：解绑（管理端回收 / C 端移除） */
	public static final String ACTION_UNBIND = "UNBIND";

	/** 船舶ID */
	private String vesselId;

	/** 商城用户ID */
	private String userId;

	/** 动作：BIND / REBIND / ROLE_CHANGE / UNBIND */
	private String action;

	/** 本次绑定来源，取值见 {@link com.aryn.cloud.vessel.api.constant.VesselMemberBindSource} */
	private String bindSource;

	/** 变更前角色（BIND 时为空） */
	private String oldMemberRole;

	/** 变更后角色（UNBIND 时为空） */
	private String newMemberRole;

	/** 变更/绑定原因（审核偏离理由、解绑原因等） */
	private String reason;

	/** 操作人ID（系统自动流程为空） */
	private String operatorId;

	/** 操作人姓名快照 */
	private String operatorName;

}
