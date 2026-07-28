package com.aryn.cloud.order.delivery.controller.staff;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryCompleteRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryExceptionRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryItemCheckRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryPickupRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskStaffVO;
import com.aryn.cloud.order.delivery.service.DeliveryTaskStaffService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/staff")
@Tag(name = "配送员履约", description = "商城配送员任务与履约操作")
public class DeliveryTaskStaffController {

	private final DeliveryTaskStaffService deliveryTaskStaffService;

	@GetMapping("/tasks")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "我的配送任务")
	public Result<IPage<DeliveryTaskStaffVO>> page(Page<OrderDeliveryTask> page,
			@RequestParam(required = false) String status) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.page(page, status));
	}

	@GetMapping("/tasks/{id}")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "我的配送任务详情")
	public Result<DeliveryTaskStaffVO> get(@PathVariable String id) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.get(id));
	}

	@PostMapping("/tasks/{id}/picking/start")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "开始配货")
	public Result<Boolean> startPicking(@PathVariable String id,
			@RequestBody @Valid DeliveryPickupRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.startPicking(id, request));
	}

	@PutMapping("/tasks/{id}/items/{itemId}/checked")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "核对配送商品")
	public Result<Boolean> checkItem(@PathVariable String id, @PathVariable String itemId,
			@RequestBody @Valid DeliveryItemCheckRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.checkItem(id, itemId, request));
	}

	@PostMapping("/tasks/{id}/pickup")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "确认取货")
	public Result<Boolean> pickup(@PathVariable String id, @RequestBody @Valid DeliveryPickupRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.pickup(id, request));
	}

	@PostMapping("/tasks/{id}/delivered")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "提交送达")
	public Result<Boolean> complete(@PathVariable String id,
			@RequestBody @Valid DeliveryCompleteRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.complete(id, request));
	}

	@PostMapping("/tasks/{id}/exception")
	@SaCheckPermission("order:delivery:execute")
	@Operation(summary = "上报配送异常")
	public Result<Boolean> reportException(@PathVariable String id,
			@RequestBody @Valid DeliveryExceptionRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskStaffService.reportException(id, request));
	}

}
