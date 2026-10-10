
package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.DeliveryBatchPickDTO;
import com.aryn.cloud.order.api.dto.DeliveryPullOrdersDTO;
import com.aryn.cloud.order.api.dto.DeliverySortDTO;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.api.vo.DeliveryCandidateOrderVO;
import com.aryn.cloud.order.api.vo.DeliveryWorkbenchVO;
import com.aryn.cloud.order.security.DeliveryAccessGuard;
import com.aryn.cloud.order.service.IDeliveryTaskItemService;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/delivery/trip")
@Tag(description = "app-delivery-trip", name = "配送员出车单-API")
public class AppDeliveryTripController {

	private final IDeliveryTripService deliveryTripService;

	private final IDeliveryTaskService deliveryTaskService;

	private final IDeliveryTaskItemService deliveryTaskItemService;

	private final DeliveryAccessGuard deliveryAccessGuard;

	@Operation(summary = "当前进行中的出车单")
	@GetMapping("/active")
	public Result<DeliveryTrip> active() {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.getActiveTrip(staffId));
	}

	@Operation(summary = "工作台首页数据（统计+全部在途趟次）")
	@GetMapping("/workbench")
	public Result<DeliveryWorkbenchVO> workbench() {
		String staffId = getCurrentStaffId();
		DeliveryWorkbenchVO vo = new DeliveryWorkbenchVO();
		vo.setPendingTaskCount(deliveryTaskService.countPendingTasks(staffId));
		vo.setTodayDoneCount(deliveryTaskService.countTodayDoneTasks(staffId));
		vo.setTrips(deliveryTripService.listActiveTripBriefs(staffId));
		return Result.success(vo);
	}

	@Operation(summary = "出车单详情")
	@GetMapping("/{id}")
	public Result<DeliveryTrip> detail(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		DeliveryTrip trip = deliveryTripService.getTripDetail(id);
		assertOwned(trip == null ? null : trip.getStaffId(), staffId, "无权查看该出车单");
		return Result.success(trip);
	}

	@Operation(summary = "开始配货")
	@SysLog("开始配货")
	@PostMapping("/{id}/start-loading")
	public Result<Boolean> startLoading(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTripService.startLoading(id, staffId));
	}

	@Operation(summary = "全部取货明细（按任务返回，明细在 itemList 含取货状态）")
	@GetMapping("/{id}/pick-list")
	public Result<List<DeliveryTask>> pickList(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		DeliveryTrip trip = deliveryTripService.getTripDetail(id);
		if (trip == null) {
			return Result.success(null);
		}
		assertOwned(trip.getStaffId(), staffId, "无权查看该出车单");
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

	@Operation(summary = "批量确认/取消取货（配货汇总行一次勾选多条明细）")
	@SysLog("批量确认取货")
	@PostMapping("/{id}/items/batch-pick")
	public Result<Integer> batchPick(@PathVariable String id,
			@RequestBody @Valid DeliveryBatchPickDTO dto) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskItemService.batchPick(id, dto.getItemIds(), dto.isPicked(), staffId));
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

	@Operation(summary = "可拉进本趟的候选订单（我的未完成 / 未派送订单）")
	@GetMapping("/{id}/pull-candidates")
	public Result<List<DeliveryCandidateOrderVO>> pullCandidates(@PathVariable String id,
			@RequestParam(required = false) String source,
			@RequestParam(required = false) String keyword) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskService.listPullCandidates(id, staffId, source, keyword));
	}

	@Operation(summary = "把订单拉进本趟（今日已有未完成 / 未派送订单 / 临时新增共用）")
	@SysLog("拉单并入出车单")
	@PostMapping("/{id}/pull-orders")
	public Result<Integer> pullOrders(@PathVariable String id, @RequestBody @Valid DeliveryPullOrdersDTO dto) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskService.pullOrdersIntoTrip(id, staffId, dto.getOrderIds()));
	}

	/**
	 * 获取当前登录的配送员ID（动态校验配送员资料与配送资格）
	 */
	private String getCurrentStaffId() {
		return deliveryAccessGuard.requireCurrentStaff().getId();
	}

	private void assertOwned(String ownerId, String staffId, String message) {
		if (ownerId == null || !ownerId.equals(staffId)) {
			throw new ArynBusinessException(message);
		}
	}

}
