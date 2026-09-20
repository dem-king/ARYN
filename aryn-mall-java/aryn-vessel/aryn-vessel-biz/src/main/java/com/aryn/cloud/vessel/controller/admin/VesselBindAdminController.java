package com.aryn.cloud.vessel.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.service.IVesselBindService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 船舶绑定申请审核（管理端）。
 *
 * <p>业务员在移动端提交「认领船舶」申请后由运营在此审核。
 * 审核通过时若船舶尚未录入系统，可先建船再绑定——对应「用户比运营更早接触到船」的场景。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/bind-applies")
@Tag(description = "vessel-bind-admin", name = "船舶绑定申请审核")
public class VesselBindAdminController {

	private final IVesselBindService vesselBindService;

	@Operation(summary = "绑定申请分页（可按状态与船名过滤）")
	@SaCheckPermission("vessel:bindapply:page")
	@GetMapping("/page")
	public Result<IPage<VesselBindApplyVO>> page(Page<VesselBindApplyVO> page,
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "vesselName", required = false) String vesselName) {
		return Result.success(
				vesselBindService.adminPage(page, SecurityUtils.getTenantId(), status, vesselName));
	}

	@Operation(summary = "审核通过（船不存在时可先建船再绑定），返回实际绑定的船舶ID")
	@SaCheckPermission("vessel:bindapply:audit")
	@PostMapping("/{id}/approve")
	public Result<String> approve(@PathVariable String id, @RequestBody(required = false) VesselBindAuditDTO dto) {
		return Result.success(vesselBindService.approveApply(SecurityUtils.getTenantId(), SecurityUtils.getUserId(),
				id, dto));
	}

	@Operation(summary = "审核驳回（必须填写原因）")
	@SaCheckPermission("vessel:bindapply:audit")
	@PostMapping("/{id}/reject")
	public Result<Void> reject(@PathVariable String id, @RequestBody VesselBindAuditDTO dto) {
		vesselBindService.rejectApply(SecurityUtils.getTenantId(), SecurityUtils.getUserId(), id,
				dto == null ? null : dto.getAuditRemark());
		return Result.success();
	}

}
