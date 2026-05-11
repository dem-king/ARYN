package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/order")
@Tag(name = "分销订单", description = "分销订单管理")
public class DistributionOrderController {

	private final IDistributionOrderService distributionOrderService;

	private final IDistributionSettlementService distributionSettlementService;

	@Operation(summary = "分销订单分页")
	@SaCheckPermission("promotion:distributionorder:page")
	@GetMapping("/page")
	public Result<IPage<DistributionOrder>> page(Page<DistributionOrder> page, DistributionOrder query) {
		return Result.success(distributionOrderService.selectAdminPage(page, query));
	}

	@Operation(summary = "分销订单详情")
	@SaCheckPermission("promotion:distributionorder:get")
	@GetMapping("/{id}")
	public Result<DistributionOrder> get(@PathVariable String id) {
		return Result.success(distributionOrderService.getById(id));
	}

	@SysLog("手工触发结算")
	@Operation(summary = "手工触发结算")
	@SaCheckPermission("promotion:distributionorder:settle")
	@PostMapping("/settle")
	public Result<Boolean> settle(@Valid @RequestBody DistributionSettleDTO dto) {
		return Result.success(distributionSettlementService.settleOrder(dto));
	}

}
