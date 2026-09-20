package com.aryn.cloud.vessel.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.vessel.service.IVesselBindService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 船舶自助绑定 C 端接口。
 *
 * <p>此前 C 端只有 3 个 GET（我的船舶 / 靠港计划 / 上下文），未绑定用户没有任何出路。
 * 本控制器补上三条通道：业务员认领船舶、成员现场拉人、邀请码自助加入。
 *
 * @author aryn
 * @since 2026/9/19
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
@Tag(description = "vessel-bind-app", name = "船舶自助绑定")
public class VesselBindAppController {

	private final IVesselBindService vesselBindService;

	// ---------------------------------------------------------------------
	// 申请：业务员认领船舶 / 船员申请上船
	// ---------------------------------------------------------------------

	@Operation(summary = "提交船舶绑定申请（业务员认领船舶 applyRole=4 / 船员申请 applyRole=2）")
	@PostMapping("/bind-applies")
	public Result<VesselBindApplyVO> submitApply(@Valid @RequestBody VesselBindApplyDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.submitApply(user.getTenantId(), user.getUserId(), dto));
	}

	@Operation(summary = "我的绑定申请列表")
	@GetMapping("/bind-applies/my")
	public Result<List<VesselBindApplyVO>> myApplies() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.myApplies(user.getTenantId(), user.getUserId()));
	}

	@Operation(summary = "撤回我的待审核申请")
	@PostMapping("/bind-applies/{id}/cancel")
	public Result<Void> cancelApply(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		vesselBindService.cancelApply(user.getTenantId(), user.getUserId(), id);
		return Result.success();
	}

	// ---------------------------------------------------------------------
	// 成员管理（业务员现场拉人）
	// ---------------------------------------------------------------------

	@Operation(summary = "船舶成员列表（仅该船成员可见）")
	@GetMapping("/{vesselId}/members")
	public Result<List<VesselMemberVO>> members(@PathVariable String vesselId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.listMembers(user.getTenantId(), user.getUserId(), vesselId));
	}

	@Operation(summary = "添加船舶成员（限发起人/采购确认人/业务员）")
	@PostMapping("/{vesselId}/members")
	public Result<VesselMemberVO> addMember(@PathVariable String vesselId, @Valid @RequestBody VesselMemberAddDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				vesselBindService.addMember(user.getTenantId(), user.getUserId(), vesselId, dto));
	}

	@Operation(summary = "移除船舶成员（限发起人/采购确认人/业务员）")
	@DeleteMapping("/{vesselId}/members/{memberId}")
	public Result<Void> removeMember(@PathVariable String vesselId, @PathVariable String memberId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		vesselBindService.removeMember(user.getTenantId(), user.getUserId(), vesselId, memberId);
		return Result.success();
	}

	@Operation(summary = "检索可添加的商城用户（手机号/昵称/用户ID）")
	@GetMapping("/{vesselId}/member-candidates")
	public Result<List<VesselMemberVO>> memberCandidates(@PathVariable String vesselId,
			@RequestParam(value = "keyword", required = false) String keyword) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				vesselBindService.searchCandidates(user.getTenantId(), user.getUserId(), vesselId, keyword));
	}

	// ---------------------------------------------------------------------
	// 邀请码
	// ---------------------------------------------------------------------

	@Operation(summary = "生成邀请码（限发起人/采购确认人/业务员）")
	@PostMapping("/{vesselId}/invite-codes")
	public Result<VesselInviteCode> generateInviteCode(@PathVariable String vesselId,
			@RequestParam(value = "maxUses", required = false) Integer maxUses,
			@RequestParam(value = "expireHours", required = false) Integer expireHours,
			@RequestParam(value = "remark", required = false) String remark) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.generateInviteCode(user.getTenantId(), user.getUserId(), vesselId,
				maxUses, expireHours, remark));
	}

	@Operation(summary = "我生成的有效邀请码")
	@GetMapping("/{vesselId}/invite-codes")
	public Result<List<VesselInviteCode>> myInviteCodes(@PathVariable String vesselId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.myInviteCodes(user.getTenantId(), user.getUserId(), vesselId));
	}

	@Operation(summary = "撤销邀请码（仅生成人）")
	@DeleteMapping("/invite-codes/{codeId}")
	public Result<Void> revokeInviteCode(@PathVariable String codeId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		vesselBindService.revokeInviteCode(user.getTenantId(), user.getUserId(), codeId);
		return Result.success();
	}

	@Operation(summary = "使用邀请码加入船舶")
	@PostMapping("/invite-codes/redeem")
	public Result<VesselMemberVO> redeemInviteCode(@RequestParam("code") String code) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselBindService.redeemInviteCode(user.getTenantId(), user.getUserId(), code));
	}

}
