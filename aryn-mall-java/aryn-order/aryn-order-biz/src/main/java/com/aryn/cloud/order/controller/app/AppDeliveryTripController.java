
package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.DeliverySortDTO;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送员出车单
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/delivery/trip")
@Tag(description = "app-delivery-trip", name = "配送员出车单-API")
public class AppDeliveryTripController {

	private final IDeliveryTripService deliveryTripService;

	private final IDeliveryTaskService deliveryTaskService;

	private final IDeliveryTaskItemService deliveryTaskItemService;

	private final IDeliveryStaffService deliveryStaffService;

	@Operation(summary = "当前进行中的出车单")
	@GetMapping("/active")
	public Result<DeliveryTrip> active() {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.getActiveTrip(staffId));
	}

	@Operation(summary = "出车单详情")
	@GetMapping("/{id}")
	public Result<DeliveryTrip> detail(@PathVariable String id) {
		return Result.success(deliveryTripService.getTripDetail(id));
	}

	@Operation(summary = "开始配货")
	@SysLog("开始配货")
	@PostMapping("/{id}/start-loading")
	public Result<Boolean> startLoading(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.startLoading(id, staffId));
	}

	@Operation(summary = "全部取货清单（按订单分组）")
	@GetMapping("/{id}/pick-list")
	public Result<List<DeliveryTask>> pickList(@PathVariable String id) {
		DeliveryTrip trip = deliveryTripService.getTripDetail(id);
		if (trip == null) {
			return Result.success(null);
		}
		return Result.success(trip.getTaskList());
	}

	@Operation(summary = "逐项确认取货")
	@SysLog("确认取货")
	@PostMapping("/{id}/items/{itemId}/pick")
	public Result<Boolean> pick(@PathVariable String id, @PathVariable String itemId) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskItemService.pick(itemId, staffId));
	}

	@Operation(summary = "取消确认取货")
	@SysLog("取消确认取货")
	@PostMapping("/{id}/items/{itemId}/unpick")
	public Result<Boolean> unpick(@PathVariable String id, @PathVariable String itemId) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskItemService.unpick(itemId, staffId));
	}

	@Operation(summary = "装货完毕出发")
	@SysLog("装货完毕出发")
	@PostMapping("/{id}/depart")
	public Result<Boolean> depart(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.depart(id, staffId));
	}

	@Operation(summary = "调整送货顺序")
	@SysLog("调整送货顺序")
	@PutMapping("/{id}/sort")
	public Result<Boolean> sort(@PathVariable String id, @RequestBody @Valid DeliverySortDTO dto) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.adjustSort(id, staffId, dto.getTaskIds()));
	}

	/**
	 * 获取当前登录的配送员ID
	 */
	private String getCurrentStaffId() {
		String userId = SecurityUtils.getUserId();
		com.aryn.cloud.order.api.entity.DeliveryStaff staff = deliveryStaffService.getByUserId(userId);
		if (staff == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("当前用户不是配送员");
		}
		return staff.getId();
	}

}