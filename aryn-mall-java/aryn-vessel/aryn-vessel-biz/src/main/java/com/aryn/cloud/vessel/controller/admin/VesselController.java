package com.aryn.cloud.vessel.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.dto.VesselUnbindDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.service.IVesselBindService;
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
import org.springframework.web.bind.annotation.RequestParam;
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

	/** 解绑复用绑定服务的既有实现（邀请码作废、发起人保护、审计、通知都在那里收口） */
	private final IVesselBindService vesselBindService;

	@Operation(summary = "船舶分页列表")
	@SaCheckPermission("vessel:vessel:page")
	@GetMapping("/page")
	public Result<IPage<VesselInfo>> page(Page<VesselInfo> page, VesselInfo query) {
		return Result.success(vesselService.pageVessels(ArynTenantContextHolder.getTenantId(), page, query));
	}

	@Operation(summary = "新增船舶")
	@SaCheckPermission("vessel:vessel:save")
	@PostMapping
	public Result<VesselInfo> save(@RequestBody VesselInfo vessel) {
		return Result.success(vesselService.saveVessel(ArynTenantContextHolder.getTenantId(), vessel));
	}

	@Operation(summary = "修改船舶（含停用）")
	@SaCheckPermission("vessel:vessel:update")
	@PutMapping("/{id}")
	public Result<VesselInfo> update(@PathVariable String id, @RequestBody VesselInfo vessel) {
		vessel.setId(id);
		return Result.success(vesselService.updateVessel(ArynTenantContextHolder.getTenantId(), vessel));
	}

	@Operation(summary = "船舶成员列表")
	@SaCheckPermission("vessel:member:list")
	@GetMapping("/{id}/members")
	public Result<List<VesselMember>> members(@PathVariable String id) {
		return Result.success(vesselService.listMembers(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "绑定船舶成员")
	@SaCheckPermission("vessel:member:save")
	@PostMapping("/{id}/members")
	public Result<VesselMember> addMember(@PathVariable String id, @RequestBody VesselMember member) {
		member.setVesselId(id);
		return Result.success(vesselService.addMember(ArynTenantContextHolder.getTenantId(), member));
	}

	/**
	 * 回收（解绑）成员：误绑纠错入口。
	 *
	 * <p>为什么要补这个接口：审核通过后此前管理端没有任何退路，误绑只能让用户自己去 C 端退出，
	 * 而用户往往正是不会操作的那个人。这里委托既有的
	 * {@code IVesselBindService#adminUnbindMember}，不重复实现解绑逻辑
	 * （邀请码作废、发起人保护、审计留痕、站内信通知都在那里统一收口）。
	 *
	 * <p>路径挂在本控制器而不是 VesselBindAdminController：语义属于「船舶成员管理」，
	 * 与上面的绑定接口同页，权限沿用 {@code vessel:member:save}（与所在页面一致）。
	 */
	@Operation(summary = "解绑船舶成员（误绑纠错）")
	@SaCheckPermission("vessel:member:save")
	@PostMapping("/{id}/members/{userId}/unbind")
	public Result<Void> unbindMember(@PathVariable String id, @PathVariable String userId,
			@RequestBody(required = false) VesselUnbindDTO dto) {
		vesselBindService.adminUnbindMember(ArynTenantContextHolder.getTenantId(), SecurityUtils.getUserId(), id,
				userId, dto == null ? null : dto.getReason());
		return Result.success();
	}

	@Operation(summary = "靠港计划列表")
	@SaCheckPermission("vessel:call:list")
	@GetMapping("/{id}/calls")
	public Result<List<VesselCall>> calls(@PathVariable String id) {
		return Result.success(vesselService.listCalls(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "靠港日历（时间区间，跨船舶）")
	@SaCheckPermission("vessel:call:list")
	@GetMapping("/calls/calendar")
	public Result<List<com.aryn.cloud.vessel.api.vo.VesselCallCalendarVO>> calendar(
			@RequestParam("start") String start, @RequestParam("end") String end) {
		java.time.LocalDateTime startTime = java.time.LocalDateTime.parse(start,
				java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		java.time.LocalDateTime endTime = java.time.LocalDateTime.parse(end,
				java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		return Result.success(vesselService.calendar(ArynTenantContextHolder.getTenantId(), startTime, endTime));
	}

	@Operation(summary = "待处理靠港申报（海员申报且尚未排产）")
	@SaCheckPermission("vessel:call:list")
	@GetMapping("/calls/declared")
	public Result<List<VesselCall>> declaredCalls() {
		return Result.success(vesselService.listDeclaredCalls(ArynTenantContextHolder.getTenantId()));
	}

	@Operation(summary = "新增靠港计划")
	@SaCheckPermission("vessel:call:save")
	@PostMapping("/{id}/calls")
	public Result<VesselCall> addCall(@PathVariable String id, @RequestBody VesselCall call) {
		call.setVesselId(id);
		return Result.success(vesselService.saveCall(ArynTenantContextHolder.getTenantId(), call));
	}

	@Operation(summary = "修改靠港计划（ETA/ETD/泊位/时间窗变更将自动提醒受影响用户）")
	@SaCheckPermission("vessel:call:update")
	@PutMapping("/calls/{id}")
	public Result<VesselCall> updateCall(@PathVariable String id, @RequestBody VesselCall call) {
		call.setId(id);
		call.setOperatorId(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
		call.setOperatorName(cn.dev33.satoken.stp.StpUtil.getLoginIdAsString());
		return Result.success(vesselService.updateCall(ArynTenantContextHolder.getTenantId(), call));
	}

}
