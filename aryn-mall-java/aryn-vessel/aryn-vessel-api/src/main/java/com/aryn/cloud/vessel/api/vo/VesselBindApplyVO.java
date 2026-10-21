package com.aryn.cloud.vessel.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 船舶绑定申请视图（C 端「我的申请」与管理端待审列表共用）。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "船舶绑定申请VO")
public class VesselBindApplyVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "申请ID")
	private String id;

	@Schema(description = "申请单号")
	private String applyNo;

	@Schema(description = "申请人商城用户ID")
	private String userId;

	@Schema(description = "申请人昵称（跨域回填，取不到时为空）")
	private String userNickname;

	@Schema(description = "申请人手机号")
	private String userPhone;

	@Schema(description = "申请角色：2普通船员 4业务员")
	private String applyRole;

	@Schema(description = "申请船名")
	private String applyVesselName;

	@Schema(description = "IMO 编号")
	private String applyVesselImo;

	@Schema(description = "呼号")
	private String applyVesselCallSign;

	@Schema(description = "常靠港口")
	private String applyPortName;

	@Schema(description = "真实姓名")
	private String realName;

	@Schema(description = "联系电话")
	private String phone;

	@Schema(description = "船上职务 / 业务员工号")
	private String position;

	@Schema(description = "补充说明")
	private String remark;

	@Schema(description = "状态：1待审核 2已通过 3已驳回 4已取消")
	private String status;

	@Schema(description = "审核通过后绑定的船舶ID")
	private String matchedVesselId;

	@Schema(description = "审核通过后绑定的船舶名称")
	private String matchedVesselName;

	/**
	 * 申请船名与最终绑定船名不一致（归一化后仍不同）。
	 *
	 * <p>存在的理由：C 端「我的申请」此前只显示「已通过」，用户看到的是自己申请时填的船名，
	 * 实际绑上的可能是另一条船（误绑事故的现场）。这个标记让不一致在用户侧可见，
	 * 管理端也据此在列表里提示运营复核。
	 */
	@Schema(description = "申请船名与最终绑定船名不一致（归一化后仍不同）")
	private Boolean vesselMismatch;

	/**
	 * 申请人当前是否仍在该船在船成员中。
	 *
	 * <p>存在的理由：审核单一旦置为「已通过」就不再变化，解绑后申请单仍是「已通过」，
	 * 管理端据此渲染的「解绑」按钮会残留，再点只能报「该用户不是本船在船成员」。
	 * 由服务端批量给出真实在船状态，前端据此决定按钮是否可用。
	 * 未通过 / 未绑定船舶的申请为 null。
	 */
	@Schema(description = "申请人是否仍在绑定船舶的在船成员中（未通过/未绑定时为 null）")
	private Boolean memberActive;

	@Schema(description = "审核意见 / 驳回原因")
	private String auditRemark;

	@Schema(description = "审核时间")
	private LocalDateTime auditTime;

	@Schema(description = "提交时间")
	private LocalDateTime createTime;

}
