package com.aryn.cloud.vessel.service;

import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.api.vo.VesselBindPrecheckVO;
import com.aryn.cloud.vessel.api.vo.VesselBindVesselOptionVO;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 船舶自助绑定服务。
 *
 * <p>背景：此前 C 端**没有任何自助绑定入口**——VesselAppController 只有 3 个 GET，
 * 唯一绑定接口是管理端 POST /admin/{id}/members，未绑定用户进不了船供链路且无出路。
 *
 * <p>本服务提供三条通道，对应业务上的三个场景：
 * <ol>
 *   <li><b>业务员认领船舶</b>（{@link #submitApply} applyRole=4 + {@link #approveApply}）
 *       —— 地推冷启动。业务员第一次跑一条新船时提交申请，运营审核后成为该船成员。</li>
 *   <li><b>业务员现场拉人</b>（{@link #addMember}）
 *       —— 日常主路径。业务员已在船上，掏出手机把船员加进来，无需对方确认。</li>
 *   <li><b>邀请码自助加入</b>（{@link #generateInviteCode} / {@link #redeemInviteCode}）
 *       —— 业务员不在场时，船员之间互相拉人。</li>
 * </ol>
 *
 * <p><b>权限口径</b>：可添加成员的角色 = 发起人(1) / 采购确认人(3) / 业务员(4)；
 * 普通船员(2) 不可添加。
 *
 * @author aryn
 * @since 2026/9/19
 */
public interface IVesselBindService {

	// ---------------------------------------------------------------------
	// 申请：业务员认领船舶 / 船员申请上船
	// ---------------------------------------------------------------------

	/**
	 * 提交绑定申请。同一用户存在待审核申请时拒绝重复提交。
	 */
	VesselBindApplyVO submitApply(String tenantId, String userId, VesselBindApplyDTO dto);

	/**
	 * 我的申请列表（倒序）。
	 */
	List<VesselBindApplyVO> myApplies(String tenantId, String userId);

	/**
	 * 撤回自己的待审核申请。
	 */
	void cancelApply(String tenantId, String userId, String applyId);

	// ---------------------------------------------------------------------
	// 成员管理
	// ---------------------------------------------------------------------

	/**
	 * 船舶成员列表（仅该船成员可见）。
	 */
	List<VesselMemberVO> listMembers(String tenantId, String userId, String vesselId);

	/**
	 * 添加成员（业务员现场拉人）。操作者须为 1/3/4 角色。
	 */
	VesselMemberVO addMember(String tenantId, String operatorUserId, String vesselId, VesselMemberAddDTO dto);

	/**
	 * 移除成员。操作者须为 1/3/4 角色，且不能移除自己。
	 */
	void removeMember(String tenantId, String operatorUserId, String vesselId, String memberId);

	/**
	 * 按关键字检索可添加的商城用户（手机号/昵称/用户ID），并标记是否已在船上。
	 */
	List<VesselMemberVO> searchCandidates(String tenantId, String userId, String vesselId, String keyword);

	/**
	 * 本人退出船舶（换船跑船的自助出路）。发起人不允许自助退出，
	 * 退出后本人在该船生成的有效邀请码一并作废。
	 */
	void leaveShip(String tenantId, String userId, String vesselId);

	// ---------------------------------------------------------------------
	// 邀请码
	// ---------------------------------------------------------------------

	/**
	 * 生成邀请码（操作者须为 1/3/4 角色）。同船重复生成会先撤销旧的有效码。
	 */
	VesselInviteCode generateInviteCode(String tenantId, String userId, String vesselId, Integer maxUses,
			Integer expireHours, String remark);

	/**
	 * 查看我生成的、仍有效的邀请码。
	 */
	List<VesselInviteCode> myInviteCodes(String tenantId, String userId, String vesselId);

	/**
	 * 撤销邀请码（仅生成人本人）。
	 */
	void revokeInviteCode(String tenantId, String userId, String codeId);

	/**
	 * 使用邀请码加入船舶。
	 */
	VesselMemberVO redeemInviteCode(String tenantId, String userId, String code);

	// ---------------------------------------------------------------------
	// 管理端审核
	// ---------------------------------------------------------------------

	/**
	 * 待审分页（可按状态与船名过滤）。
	 */
	IPage<VesselBindApplyVO> adminPage(Page<VesselBindApplyVO> page, String tenantId, String status, String vesselName);

	/**
	 * 审核预检：打开审核弹窗时告诉运营「这条申请的船在不在系统里」。
	 *
	 * <p>按申请单填写的 IMO/呼号、船名依次在在营船舶里检索，返回一句话结论与候选列表，
	 * 供前端预选审核方式。只读，不写库。
	 */
	VesselBindPrecheckVO precheckApply(String tenantId, String applyId);

	/**
	 * 候选船舶检索（审核弹窗「绑定已有船舶」下拉用）。
	 *
	 * <p>仅返回在营船舶，且只带认船需要的字段（不下发整份档案）。
	 * 关键字为空时返回最近创建的若干条，便于直接点选。
	 */
	List<VesselBindVesselOptionVO> searchVesselOptions(String tenantId, String keyword);

	/**
	 * 审核通过。船舶不存在时可先建船再绑定（对应「船还没录入系统」的场景）。
	 *
	 * @return 实际绑定的船舶ID
	 */
	String approveApply(String tenantId, String adminUserId, String applyId, VesselBindAuditDTO dto);

	/**
	 * 审核驳回。
	 */
	void rejectApply(String tenantId, String adminUserId, String applyId, String auditRemark);

	/**
	 * 管理端解绑：把某个用户从船上移除。
	 *
	 * <p>存在的理由是「误绑需要退路」：审核通过后此前没有任何管理端解绑入口，
	 * 绑错了只能让用户自己去 C 端退出（而用户往往正是不会操作的那个人）。
	 *
	 * <p>约束与 C 端退出对称：不能移除发起人（否则该船没有管理角色），
	 * 被移除后本人的有效邀请码一并作废（与 {@link #leaveShip} 同口径）。
	 */
	void adminUnbindMember(String tenantId, String adminUserId, String vesselId, String userId, String reason);

}
