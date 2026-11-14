package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgBatchAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgInviteCodeDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgMemberDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgSaveDTO;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.BuyerOrgApply;
import com.aryn.cloud.order.api.entity.BuyerOrgInviteCode;
import com.aryn.cloud.order.api.vo.BuyerOrgApplyVO;
import com.aryn.cloud.order.api.vo.BuyerOrgMemberVO;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购主体管理端：主体档案、审核、成员与邀请码、创建/加入申请审核。
 *
 * <p>P0 先用本组接口验证审核流；正式审核页在 P1-FE-05 / P2-FE-01。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/buyer-org")
@Tag(description = "order-buyer-org", name = "采购主体管理")
public class BuyerOrgAdminController {

	private final IBuyerOrgService buyerOrgService;

	@Operation(summary = "采购主体分页")
	@SaCheckPermission("order:buyerorg:page")
	@GetMapping("/page")
	public Result<IPage<BuyerOrg>> page(Page<BuyerOrg> page,
			@RequestParam(required = false) String orgName,
			@RequestParam(required = false) String creditCode,
			@RequestParam(required = false) String orgType,
			@RequestParam(required = false) String auditStatus,
			@RequestParam(required = false) String status) {
		return Result.success(buyerOrgService.adminPage(page, ArynTenantContextHolder.getTenantId(),
			orgName, creditCode, orgType, auditStatus, status));
	}

	@Operation(summary = "采购主体详情")
	@SaCheckPermission("order:buyerorg:page")
	@GetMapping("/{id}")
	public Result<BuyerOrg> detail(@PathVariable String id) {
		return Result.success(buyerOrgService.adminGet(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "运营代建采购主体")
	@SysLog("代建采购主体")
	@SaCheckPermission("order:buyerorg:create")
	@PostMapping
	public Result<BuyerOrg> create(@Valid @RequestBody BuyerOrgSaveDTO dto) {
		return Result.success(buyerOrgService.adminCreate(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), dto));
	}

	@Operation(summary = "修改采购主体资料")
	@SysLog("修改采购主体")
	@SaCheckPermission("order:buyerorg:edit")
	@PutMapping("/{id}")
	public Result<Boolean> update(@PathVariable String id, @Valid @RequestBody BuyerOrgSaveDTO dto) {
		buyerOrgService.adminUpdate(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, dto);
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "审核采购主体")
	@SysLog("审核采购主体")
	@SaCheckPermission("order:buyerorg:audit")
	@PostMapping("/{id}/audit")
	public Result<Boolean> audit(@PathVariable String id, @Valid @RequestBody BuyerOrgAuditDTO dto) {
		buyerOrgService.adminAudit(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, dto);
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "批量审核/停用采购主体")
	@SysLog("批量审核采购主体")
	@SaCheckPermission("order:buyerorg:audit")
	@PostMapping("/batch-audit")
	public Result<Integer> batchAudit(@Valid @RequestBody BuyerOrgBatchAuditDTO dto) {
		return Result.success(buyerOrgService.adminBatchAudit(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), dto));
	}

	@Operation(summary = "采购主体成员")
	@SaCheckPermission("order:buyerorg:member")
	@GetMapping("/{id}/members")
	public Result<List<BuyerOrgMemberVO>> members(@PathVariable String id) {
		return Result.success(buyerOrgService.adminListMembers(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "添加/调整采购主体成员")
	@SysLog("添加采购主体成员")
	@SaCheckPermission("order:buyerorg:member")
	@PostMapping("/{id}/members")
	public Result<BuyerOrgMemberVO> addMember(@PathVariable String id,
			@Valid @RequestBody BuyerOrgMemberDTO dto) {
		return Result.success(buyerOrgService.adminAddMember(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, dto));
	}

	@Operation(summary = "移除采购主体成员")
	@SysLog("移除采购主体成员")
	@SaCheckPermission("order:buyerorg:member")
	@DeleteMapping("/{id}/members/{userId}")
	public Result<Boolean> removeMember(@PathVariable String id, @PathVariable String userId) {
		buyerOrgService.adminRemoveMember(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, userId);
		return Result.success(Boolean.TRUE);
	}

	@Operation(summary = "生成采购主体邀请码")
	@SysLog("生成采购主体邀请码")
	@SaCheckPermission("order:buyerorg:invite")
	@PostMapping("/{id}/invite-code")
	public Result<BuyerOrgInviteCode> inviteCode(@PathVariable String id,
			@RequestBody(required = false) BuyerOrgInviteCodeDTO dto) {
		return Result.success(buyerOrgService.createInviteCode(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, dto));
	}

	@Operation(summary = "采购主体申请单分页")
	@SaCheckPermission("order:buyerorg:audit")
	@GetMapping("/apply/page")
	public Result<IPage<BuyerOrgApplyVO>> applyPage(Page<BuyerOrgApply> page,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String orgName,
			@RequestParam(required = false) String applyType) {
		return Result.success(buyerOrgService.adminApplyPage(page, ArynTenantContextHolder.getTenantId(),
			status, orgName, applyType));
	}

	@Operation(summary = "审核采购主体申请单")
	@SysLog("审核采购主体申请")
	@SaCheckPermission("order:buyerorg:audit")
	@PostMapping("/apply/{id}/audit")
	public Result<Boolean> auditApply(@PathVariable String id,
			@Valid @RequestBody BuyerOrgApplyAuditDTO dto) {
		buyerOrgService.adminAuditApply(ArynTenantContextHolder.getTenantId(),
			SecurityUtils.requireUser().getUsername(), id, dto);
		return Result.success(Boolean.TRUE);
	}

}
