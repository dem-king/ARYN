package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.CampaignSimulationDTO;
import com.aryn.cloud.promotion.api.vo.CampaignSimulationVO;
import com.aryn.cloud.promotion.service.impl.CampaignSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/campaign-simulation")
public class CampaignSimulationController {
	private final CampaignSimulationService service;

	@PostMapping
	@SaCheckPermission("promotion:campaign:simulate")
	public Result<CampaignSimulationVO> simulate(@RequestBody CampaignSimulationDTO request) {
		return Result.success(service.simulate(SecurityUtils.getUserId(), request));
	}

	@GetMapping("/{id}")
	@SaCheckPermission("promotion:campaign:simulate")
	public Result<CampaignSimulationVO> get(@PathVariable String id) {
		return Result.success(service.get(SecurityUtils.getUserId(), id, true));
	}
}
