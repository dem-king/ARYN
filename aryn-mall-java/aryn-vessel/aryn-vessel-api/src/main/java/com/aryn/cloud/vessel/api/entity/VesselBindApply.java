package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 船舶绑定申请。
 *
 * <p>承载两类申请，由 {@link #applyRole} 区分：
 * <ul>
 *   <li>{@link #ROLE_CREW} 普通船员 —— 船员自助申请（业务员不在场时的兜底）</li>
 *   <li>{@link #ROLE_SALES} 业务员 —— <b>销售业务员认领船舶</b>（地推冷启动主路径）</li>
 * </ul>
 *
 * <p>审核通过时写入 {@link #matchedVesselId}：匹配到已有船舶则直接绑定，
 * 否则运营先建船再绑定——这样「船还没录入系统」也能走通。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_bind_apply")
public class VesselBindApply extends AbstractVesselEntity {

	/** 申请角色：普通船员 */
	public static final String ROLE_CREW = "2";

	/** 申请角色：业务员 */
	public static final String ROLE_SALES = "4";

	/** 状态：待审核 */
	public static final String STATUS_PENDING = "1";

	/** 状态：已通过 */
	public static final String STATUS_APPROVED = "2";

	/** 状态：已驳回 */
	public static final String STATUS_REJECTED = "3";

	/** 状态：已取消 */
	public static final String STATUS_CANCELED = "4";

	/** 申请单号 */
	private String applyNo;

	/** 申请人商城用户ID */
	private String userId;

	/** 申请角色：2普通船员 4业务员 */
	private String applyRole;

	/** 申请人填写的船名 */
	private String applyVesselName;

	/** IMO 或呼号（选填） */
	private String applyVesselImo;

	/** 常靠港口（选填） */
	private String applyPortName;

	/** 真实姓名 */
	private String realName;

	/** 联系电话 */
	private String phone;

	/** 船上职务 / 业务员工号 */
	private String position;

	/** 补充说明 */
	private String remark;

	/** 状态：1待审核 2已通过 3已驳回 4已取消 */
	private String status;

	/** 审核通过后实际绑定的船舶ID */
	private String matchedVesselId;

	/** 审核人 */
	private String auditBy;

	/** 审核时间 */
	private LocalDateTime auditTime;

	/** 审核意见（驳回原因） */
	private String auditRemark;

}
