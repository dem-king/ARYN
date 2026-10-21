package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.dto.DashboardTargetDTO;
import com.aryn.cloud.order.api.vo.DashboardTargetVO;
import com.aryn.cloud.order.service.IDashboardTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端概览月度目标。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/order/statistics/target")
@Tag(description = "dashboardtarget", name = "概览月度目标")
public class DashboardTargetController {

	private final IDashboardTargetService dashboardTargetService;

	@GetMapping
	@Operation(summary = "获取月度目标与实际达成")
	public Result<DashboardTargetVO> getTarget(@RequestParam(required = false) String month) {
		return Result.success(dashboardTargetService.getTargetOverview(month));
	}

	@PostMapping
	@Operation(summary = "保存月度目标")
	public Result<DashboardTargetVO> saveTarget(@RequestBody DashboardTargetDTO dto) {
		return Result.success(dashboardTargetService.saveTarget(dto));
	}

}
