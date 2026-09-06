
package com.aryn.cloud.order.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryEvidence;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.security.DeliveryAccessGuard;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送员任务
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/delivery/task")
@Tag(description = "app-delivery-task", name = "配送员任务-API")
public class AppDeliveryTaskController {

	private final IDeliveryTaskService deliveryTaskService;

	private final DeliveryAccessGuard deliveryAccessGuard;

	@Operation(summary = "我的配送任务列表")
	@GetMapping("/page")
	public Result<IPage<DeliveryTask>> page(Page page, DeliveryTask deliveryTask) {
		String staffId = getCurrentStaffId();
		deliveryTask.setStaffId(staffId);
		return Result.success(deliveryTaskService.page(page,
				Wrappers.lambdaQuery(deliveryTask).orderByAsc(DeliveryTask::getSortNo)));
	}

	@Operation(summary = "任务详情")
	@GetMapping("/{id}")
	public Result<DeliveryTask> detail(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		DeliveryTask task = deliveryTaskService.getTaskDetail(id);
		assertOwned(task == null ? null : task.getStaffId(), staffId);
		return Result.success(task);
	}

	@Operation(summary = "送达（含凭证图片）")
	@SysLog("送达")
	@PostMapping("/{id}/arrive")
	public Result<Boolean> arrive(@PathVariable String id,
			@RequestParam(required = false) List<String> materialIds,
			@RequestParam(required = false) String remark) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskService.arriveWithEvidence(id, staffId, materialIds, remark));
	}

	@Operation(summary = "上报异常")
	@SysLog("上报异常")
	@PostMapping("/{id}/exception")
	public Result<Boolean> reportException(@PathVariable String id,
			@RequestParam String reasonCode,
			@RequestParam String reasonDesc,
			@RequestParam(required = false) List<String> materialIds) {
		String staffId = getCurrentStaffId();
		return Result.success(deliveryTaskService.reportException(id, staffId, reasonCode, reasonDesc, materialIds));
	}

	@Operation(summary = "查询任务凭证")
	@GetMapping("/{id}/evidence")
	public Result<List<DeliveryEvidence>> evidence(@PathVariable String id) {
		String staffId = getCurrentStaffId();
		DeliveryTask task = deliveryTaskService.getById(id);
		assertOwned(task == null ? null : task.getStaffId(), staffId);
		return Result.success(deliveryTaskService.listEvidence(id));
	}

	/**
	 * 获取当前登录的配送员ID（动态校验配送员资料与配送资格）
	 */
	private String getCurrentStaffId() {
		return deliveryAccessGuard.requireCurrentStaff().getId();
	}

	private void assertOwned(String ownerId, String staffId) {
		if (ownerId == null || !ownerId.equals(staffId)) {
			throw new ArynBusinessException("无权访问该配送任务");
		}
	}

}
