package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 船舶绑定申请审核 DTO（管理端）。
 *
 * <p>通过时二选一：
 * <ul>
 *   <li>{@link #matchedVesselId} —— 匹配到系统内已有船舶，直接绑定；</li>
 *   <li>{@link #newVesselName} —— 船尚未录入系统，先建船再绑定
 *       （可同时给出 {@link #newVesselImo} / {@link #newVesselCallSign} / {@link #newVesselType}）。</li>
 * </ul>
 * 两者都不传则先按申请单的 IMO/呼号、再按船名精确匹配在营船舶，匹配不到直接报错提示运营选择。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Data
@Schema(description = "船舶绑定申请审核DTO")
public class VesselBindAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "通过时：匹配到的已有船舶ID")
	private String matchedVesselId;

	@Schema(description = "通过时：新建船舶名称（船未录入系统时使用）")
	private String newVesselName;

	@Schema(description = "通过时：新建船舶的 IMO 编码")
	private String newVesselImo;

	@Schema(description = "通过时：新建船舶的呼号")
	private String newVesselCallSign;

	@Schema(description = "通过时：新建船舶类型（取值口径与船舶档案一致：1集装箱 2散货 3油轮 4杂货 5其他）")
	private String newVesselType;

	/**
	 * 系统内已存在同名在营船舶时，新建必须显式确认，否则拒绝。
	 * 目的：拦住「船名有出入 → 误建一条重复船舶档案」这条静默产生脏数据的路径
	 * （船名无唯一约束，重复档案会把成员、靠港、订单拆到两条船上，且全仓没有合并工具）。
	 */
	@Schema(description = "通过时：已确认新建同名船舶（系统内存在同名在营船时必填 true）")
	private Boolean confirmDuplicateName;

	/**
	 * 绑定已有船舶时，申请船与所选船身份键全部不命中（IMO/呼号均无或不一致、船名归一化后不同）时的确认位。
	 *
	 * <p>存在的理由：此前审核弹窗能把「悦航8号」的申请直接绑到「悦航7号」——零比对、零提示、零留痕。
	 * 现在跨船绑定必须由运营显式背书（确认为同一条船）并填写审核意见说明原因，
	 * 否则一律拒绝；背书后来源标记为 {@code APPLY_AUDIT_MISMATCH}，事后可按来源筛出来复核。
	 */
	@Schema(description = "通过时：已确认「申请船名与所选船舶不一致」（不一致时必须为 true）")
	private Boolean confirmVesselMismatch;

	@Schema(description = "通过时：授予的角色，缺省取申请单的 applyRole")
	private String memberRole;

	@Schema(description = "审核意见 / 驳回原因")
	private String auditRemark;

}
