package com.aryn.cloud.vessel.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.service.VesselService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 船舶上下文 C 端接口。
 *
 * @author aryn
 * @since 2026/9/11
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app")
@Tag(description = "vessel-app", name = "商城船舶上下文")
public class VesselAppController {

	private final VesselService vesselService;

	@Operation(summary = "我的船舶列表")
	@GetMapping("/my-vessels")
	public Result<List<VesselInfo>> myVessels() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselService.myVessels(user.getTenantId(), user.getUserId()));
	}

	@Operation(summary = "船舶可用靠港计划")
	@GetMapping("/{id}/calls")
	public Result<List<VesselCall>> calls(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselService.availableCalls(user.getTenantId(), user.getUserId(), id));
	}

	@Operation(summary = "当前船舶配送上下文")
	@GetMapping("/context")
	public Result<VesselContextDTO> context(@RequestParam("vesselId") String vesselId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(vesselService.currentContext(user.getTenantId(), user.getUserId(), vesselId));
	}

}
