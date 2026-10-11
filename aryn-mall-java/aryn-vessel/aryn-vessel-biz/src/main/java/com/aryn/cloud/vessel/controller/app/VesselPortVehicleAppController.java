package com.aryn.cloud.vessel.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.entity.VesselPortVehicle;
import com.aryn.cloud.vessel.service.VesselPortVehicleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 港口报备车辆 C 端接口：船员靠港外出时查询报备车辆并拨打司机电话。
 *
 * @author aryn
 * @since 2026/10/11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
@Tag(description = "vessel-port-vehicle-app", name = "商城港口报备车辆")
public class VesselPortVehicleAppController {

	private final VesselPortVehicleService portVehicleService;

	@Operation(summary = "某港口启用中的报备车辆")
	@GetMapping("/port-vehicles")
	public Result<List<VesselPortVehicle>> list(@RequestParam("portName") String portName) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(portVehicleService.listEnabledByPort(user.getTenantId(), portName));
	}

	@Operation(summary = "已登记车辆的港口清单")
	@GetMapping("/port-vehicles/ports")
	public Result<List<String>> ports() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(portVehicleService.listEnabledPorts(user.getTenantId()));
	}

}
