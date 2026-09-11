package com.aryn.cloud.vessel.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.service.VesselService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 船舶与靠港计划管理端。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@Tag(description = "vessel-admin", name = "船舶与靠港计划管理")
public class VesselController {

	private final VesselService vesselService;

	@Operation(summary = "船舶分页列表")
	@SaCheckPermission("vessel:vessel:page")
	@GetMapping("/page")
	public Result<IPage<VesselInfo>> page(Page<VesselInfo> page, VesselInfo query) {
		return Result.success(vesselService.pageVessels(SecurityUtils.getTenantId(), page, query));
	}

	@Operation(summary = "新增船舶")
	@SaCheckPermission("vessel:vessel:save")
	@PostMapping
	public Result<VesselInfo> save(@RequestBody VesselInfo vessel) {
		return Result.success(vesselService.saveVessel(SecurityUtils.getTenantId(), vessel));
	}

	@Operation(summary = "修改船舶（含停用）")
	@SaCheckPermission("vessel:vessel:update")
	@PutMapping("/{id}")
	public Result<VesselInfo> update(@PathVariable String id, @RequestBody VesselInfo vessel) {
		vessel.setId(id);
		return Result.success(vesselService.updateVessel(SecurityUtils.getTenantId(), vessel));
	}

	@Operation(summary = "船舶成员列表")
	@SaCheckPermission("vessel:member:list")
	@GetMapping("/{id}/members")
	public Result<List<VesselMember>> members(@PathVariable String id) {
		return Result.success(vesselService.listMembers(SecurityUtils.getTenantId(), id));
	}

	@Operation(summary = "绑定船舶成员")
	@SaCheckPermission("vessel:member:save")
	@PostMapping("/{id}/members")
	public Result<VesselMember> addMember(@PathVariable String id, @RequestBody VesselMember member) {
		member.setVesselId(id);
		return Result.success(vesselService.addMember(SecurityUtils.getTenantId(), member));
	}

	@Operation(summary = "靠港计划列表")
	@SaCheckPermission("vessel:call:list")
	@GetMapping("/{id}/calls")
	public Result<List<VesselCall>> calls(@PathVariable String id) {
		return Result.success(vesselService.listCalls(SecurityUtils.getTenantId(), id));
	}

	@Operation(summary = "新增靠港计划")
	@SaCheckPermission("vessel:call:save")
	@PostMapping("/{id}/calls")
	public Result<VesselCall> addCall(@PathVariable String id, @RequestBody VesselCall call) {
		call.setVesselId(id);
		return Result.success(vesselService.saveCall(SecurityUtils.getTenantId(), call));
	}

	@Operation(summary = "修改靠港计划")
	@SaCheckPermission("vessel:call:update")
	@PutMapping("/calls/{id}")
	public Result<VesselCall> updateCall(@PathVariable String id, @RequestBody VesselCall call) {
		call.setId(id);
		return Result.success(vesselService.updateCall(SecurityUtils.getTenantId(), call));
	}

}
