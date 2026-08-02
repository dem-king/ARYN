
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.dto.DeliveryAssignDTO;
import com.aryn.cloud.order.api.entity.DeliveryEvidence;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.DeliveryTaskLog;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送任务管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/task")
@Tag(description = "delivery-task", name = "配送任务管理")
public class DeliveryTaskController {

	private final IDeliveryTaskService deliveryTaskService;

	@Operation(summary = "配送任务分页列表")
	@SaCheckPermission("delivery:task:page")
	@GetMapping("/page")
	public Result<IPage<DeliveryTask>> page(Page page, DeliveryTask deliveryTask) {
		return Result.success(deliveryTaskService.page(page,
				com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(deliveryTask)
						.orderByAsc(DeliveryTask::getStatus)
						.orderByAsc(DeliveryTask::getSortNo)));
	}

	@Operation(summary = "配送任务详情")
	@SaCheckPermission("delivery:task:get")
	@GetMapping("/{id}")
	public Result<DeliveryTask> getById(@PathVariable String id) {
		return Result.success(deliveryTaskService.getTaskDetail(id));
	}

	@Operation(summary = "批量派单")
	@SysLog("批量派单")
	@SaCheckPermission("delivery:task:assign")
	@PostMapping("/assign")
	public Result<String> assign(@RequestBody @Valid DeliveryAssignDTO dto) {
		return Result.success(deliveryTaskService.assignTasks(dto));
	}

	@Operation(summary = "改派")
	@SysLog("改派")
	@SaCheckPermission("delivery:task:reassign")
	@PostMapping("/{id}/reassign")
	public Result<Boolean> reassign(@PathVariable String id, @RequestParam String staffId) {
		return Result.success(deliveryTaskService.reassign(id, staffId));
	}

	@Operation(summary = "取消任务")
	@SysLog("取消任务")
	@SaCheckPermission("delivery:task:cancel")
	@PostMapping("/{id}/cancel")
	public Result<Boolean> cancel(@PathVariable String id) {
		return Result.success(deliveryTaskService.cancel(id));
	}

	@Operation(summary = "关闭异常任务")
	@SysLog("关闭异常任务")
	@SaCheckPermission("delivery:task:exception")
	@PostMapping("/{id}/close")
	public Result<Boolean> close(@PathVariable String id, @RequestParam String reason) {
		return Result.success(deliveryTaskService.close(id, reason));
	}

	@Operation(summary = "置为待退回")
	@SysLog("置为待退回")
	@SaCheckPermission("delivery:task:return")
	@PostMapping("/{id}/return-pending")
	public Result<Boolean> returnPending(@PathVariable String id) {
		return Result.success(deliveryTaskService.returnPending(id));
	}

	@Operation(summary = "确认商品退回")
	@SysLog("确认商品退回")
	@SaCheckPermission("delivery:task:return")
	@PostMapping("/{id}/return-confirm")
	public Result<Boolean> returnConfirm(@PathVariable String id, @RequestParam(required = false) String remark) {
		return Result.success(deliveryTaskService.returnConfirm(id, remark));
	}

	@Operation(summary = "查询任务凭证")
	@SaCheckPermission("delivery:task:get")
	@GetMapping("/{id}/evidence")
	public Result<List<DeliveryEvidence>> evidence(@PathVariable String id) {
		return Result.success(deliveryTaskService.listEvidence(id));
	}

	@Operation(summary = "查询任务操作日志")
	@SaCheckPermission("delivery:task:get")
	@GetMapping("/{id}/logs")
	public Result<List<DeliveryTaskLog>> logs(@PathVariable String id) {
		return Result.success(deliveryTaskService.listLogs(id));
	}

}
