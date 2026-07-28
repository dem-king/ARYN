package com.aryn.cloud.order.delivery.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryArea;
import com.aryn.cloud.order.delivery.service.DeliveryAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/admin/areas")
@Tag(name = "商城配送范围", description = "商城配送范围管理")
public class DeliveryAreaAdminController {

	private final DeliveryAreaService deliveryAreaService;

	@GetMapping
	@SaCheckPermission("order:delivery:area")
	@Operation(summary = "配送范围列表")
	public Result<List<OrderDeliveryArea>> list() {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryAreaService.listAreas());
	}

	@PostMapping
	@SysLog("新增商城配送范围")
	@SaCheckPermission("order:delivery:area")
	@Operation(summary = "新增配送范围")
	public Result<Boolean> create(@RequestBody OrderDeliveryArea area) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryAreaService.create(area));
	}

	@PutMapping("/{id}")
	@SysLog("修改商城配送范围")
	@SaCheckPermission("order:delivery:area")
	@Operation(summary = "修改配送范围")
	public Result<Boolean> update(@PathVariable String id, @RequestBody OrderDeliveryArea area) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryAreaService.update(id, area));
	}

	@DeleteMapping("/{id}")
	@SysLog("删除商城配送范围")
	@SaCheckPermission("order:delivery:area")
	@Operation(summary = "删除配送范围")
	public Result<Boolean> remove(@PathVariable String id) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryAreaService.remove(id));
	}

}
