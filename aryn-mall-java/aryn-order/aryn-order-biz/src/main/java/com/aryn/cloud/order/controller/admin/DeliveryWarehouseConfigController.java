
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.entity.DeliveryWarehouseConfig;
import com.aryn.cloud.order.service.IDeliveryWarehouseConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 仓库配置管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/warehouse-config")
@Tag(description = "delivery-warehouse-config", name = "仓库配置管理")
public class DeliveryWarehouseConfigController {

	private final IDeliveryWarehouseConfigService deliveryWarehouseConfigService;

	@Operation(summary = "获取仓库配置")
	@SaCheckPermission("delivery:warehouse:get")
	@GetMapping
	public Result<DeliveryWarehouseConfig> get() {
		return Result.success(deliveryWarehouseConfigService.getConfig());
	}

	@Operation(summary = "保存或更新仓库配置")
	@SysLog("保存或更新仓库配置")
	@SaCheckPermission("delivery:warehouse:edit")
	@PutMapping
	public Result<Boolean> saveOrUpdate(@RequestBody DeliveryWarehouseConfig config) {
		return Result.success(deliveryWarehouseConfigService.saveOrUpdateConfig(config));
	}

}