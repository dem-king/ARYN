package com.aryn.cloud.vessel.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.entity.VesselPortVehicle;
import com.aryn.cloud.vessel.service.VesselPortVehicleService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 港口报备车辆管理端：船务公司按港口维护可在港区通行的车辆名单。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
@Tag(description = "vessel-port-vehicle-admin", name = "港口报备车辆管理")
public class VesselPortVehicleController {

	private final VesselPortVehicleService portVehicleService;

	@Operation(summary = "报备车辆分页列表")
	@SaCheckPermission("vessel:portvehicle:page")
	@GetMapping("/port-vehicles/page")
	public Result<IPage<VesselPortVehicle>> page(Page<VesselPortVehicle> page,
			@RequestParam(value = "portName", required = false) String portName,
			@RequestParam(value = "plateNo", required = false) String plateNo,
			@RequestParam(value = "driverName", required = false) String driverName,
			@RequestParam(value = "status", required = false) String status) {
		return Result.success(portVehicleService.pageVehicles(SecurityUtils.getTenantId(), page,
				portName, plateNo, driverName, status));
	}

	@Operation(summary = "港口名建议选项（已登记车辆港口 + 靠港计划港口）")
	@SaCheckPermission("vessel:portvehicle:page")
	@GetMapping("/port-vehicles/port-options")
	public Result<List<String>> portOptions() {
		return Result.success(portVehicleService.listPortOptions(SecurityUtils.getTenantId()));
	}

	@Operation(summary = "新增报备车辆")
	@SaCheckPermission("vessel:portvehicle:save")
	@PostMapping("/port-vehicles")
	public Result<VesselPortVehicle> save(@RequestBody VesselPortVehicle vehicle) {
		return Result.success(portVehicleService.saveVehicle(SecurityUtils.getTenantId(), vehicle));
	}

	@Operation(summary = "修改报备车辆")
	@SaCheckPermission("vessel:portvehicle:update")
	@PutMapping("/port-vehicles/{id}")
	public Result<VesselPortVehicle> update(@PathVariable String id, @RequestBody VesselPortVehicle vehicle) {
		vehicle.setId(id);
		return Result.success(portVehicleService.updateVehicle(SecurityUtils.getTenantId(), vehicle));
	}

	@Operation(summary = "删除报备车辆")
	@SaCheckPermission("vessel:portvehicle:delete")
	@DeleteMapping("/port-vehicles/{id}")
	public Result<Void> delete(@PathVariable String id) {
		portVehicleService.deleteVehicle(SecurityUtils.getTenantId(), id);
		return Result.success();
	}

}
