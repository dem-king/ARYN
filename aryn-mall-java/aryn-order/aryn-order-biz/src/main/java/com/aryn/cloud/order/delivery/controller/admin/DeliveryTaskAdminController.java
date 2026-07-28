package com.aryn.cloud.order.delivery.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.dto.DeliveryAssignRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryCloseRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryReassignRequest;
import com.aryn.cloud.order.api.delivery.dto.DeliveryReturnRequest;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskAdminVO;
import com.aryn.cloud.order.delivery.service.DeliveryTaskAdminService;
import com.aryn.cloud.upms.api.dto.DeliveryStaffQuery;
import com.aryn.cloud.upms.api.remote.RemoteDeliveryStaffService;
import com.aryn.cloud.upms.api.vo.DeliveryStaffPageVO;
import com.aryn.cloud.upms.api.vo.MaterialAccessVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/admin")
@Tag(name = "商城配送任务", description = "商城配送派单与异常处理")
public class DeliveryTaskAdminController {

	private final DeliveryTaskAdminService deliveryTaskAdminService;

	@DubboReference
	private final RemoteDeliveryStaffService remoteDeliveryStaffService;

	@GetMapping("/tasks")
	@SaCheckPermission("order:delivery:page")
	@Operation(summary = "配送任务分页")
	public Result<IPage<DeliveryTaskAdminVO>> page(Page<OrderDeliveryTask> page, OrderDeliveryTask query) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.page(page, query));
	}

	@GetMapping("/tasks/{id}")
	@SaCheckPermission("order:delivery:get")
	@Operation(summary = "配送任务详情")
	public Result<DeliveryTaskAdminVO> get(@PathVariable String id) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.get(id));
	}

	@GetMapping("/tasks/{id}/evidences/{evidenceId}/access")
	@SaCheckPermission("order:delivery:get")
	@Operation(summary = "刷新配送凭证短期访问地址")
	public Result<MaterialAccessVO> getEvidenceAccess(@PathVariable String id,
			@PathVariable String evidenceId) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.getEvidenceAccess(id, evidenceId));
	}

	@GetMapping("/staff/candidates")
	@SaCheckPermission("order:delivery:assign")
	@Operation(summary = "配送员候选列表")
	public Result<DeliveryStaffPageVO> candidates(DeliveryStaffQuery query) {
		ArynUser operator = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		query.setTenantId(operator.getTenantId());
		return Result.success(remoteDeliveryStaffService.queryCandidates(query));
	}

	@PostMapping("/tasks/{id}/assign")
	@SysLog("商城配送首次派单")
	@SaCheckPermission("order:delivery:assign")
	@Operation(summary = "首次派单")
	public Result<Boolean> assign(@PathVariable String id, @RequestBody @Valid DeliveryAssignRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.assign(id, request));
	}

	@PostMapping("/tasks/{id}/reassign")
	@SysLog("商城配送改派")
	@SaCheckPermission("order:delivery:reassign")
	@Operation(summary = "改派或再次配送")
	public Result<Boolean> reassign(@PathVariable String id, @RequestBody @Valid DeliveryReassignRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.reassign(id, request));
	}

	@PostMapping("/tasks/{id}/close")
	@SysLog("关闭商城配送任务")
	@SaCheckPermission("order:delivery:exception")
	@Operation(summary = "关闭配送任务")
	public Result<Boolean> close(@PathVariable String id, @RequestBody @Valid DeliveryCloseRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.close(id, request));
	}

	@PostMapping("/tasks/{id}/return-pending")
	@SysLog("商城配送进入待退回")
	@SaCheckPermission("order:delivery:return")
	@Operation(summary = "进入待退回")
	public Result<Boolean> markReturnPending(@PathVariable String id,
			@RequestBody @Valid DeliveryReturnRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.markReturnPending(id, request));
	}

	@PostMapping("/tasks/{id}/return-confirm")
	@SysLog("确认商城配送商品退回")
	@SaCheckPermission("order:delivery:return")
	@Operation(summary = "确认商品退回")
	public Result<Boolean> confirmReturn(@PathVariable String id,
			@RequestBody @Valid DeliveryReturnRequest request) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(deliveryTaskAdminService.confirmReturn(id, request));
	}

}
