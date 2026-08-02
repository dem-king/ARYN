
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.dto.DeliveryStaffDTO;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.enums.DeliveryStaffStatusEnum;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配送员管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/staff")
@Tag(description = "delivery-staff", name = "配送员管理")
public class DeliveryStaffController {

	private final IDeliveryStaffService deliveryStaffService;

	@Operation(summary = "配送员详情")
	@SaCheckPermission("delivery:staff:get")
	@GetMapping("{id}")
	public Result<DeliveryStaff> getById(@PathVariable String id) {
		return Result.success(deliveryStaffService.getById(id));
	}

	@Operation(summary = "配送员列表")
	@SaCheckPermission("delivery:staff:list")
	@GetMapping("list")
	public Result<List<DeliveryStaff>> list() {
		return Result.success(deliveryStaffService.list());
	}

	@Operation(summary = "配送员分页列表")
	@SaCheckPermission("delivery:staff:page")
	@GetMapping("/page")
	public Result<IPage<DeliveryStaff>> page(Page page, DeliveryStaff deliveryStaff) {
		return Result.success(deliveryStaffService.page(page,
				com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(deliveryStaff)));
	}

	@Operation(summary = "新增配送员")
	@SysLog("新增配送员")
	@SaCheckPermission("delivery:staff:add")
	@PostMapping
	public Result<Boolean> save(@RequestBody @Valid DeliveryStaffDTO dto) {
		DeliveryStaff staff = new DeliveryStaff();
		staff.setUserId(dto.getUserId());
		staff.setStaffName(dto.getStaffName());
		staff.setStaffPhone(dto.getStaffPhone());
		staff.setStatus(dto.getStatus() == null ? DeliveryStaffStatusEnum.OFFLINE.getCode() : dto.getStatus());
		staff.setVehicleInfo(dto.getVehicleInfo());
		return Result.success(deliveryStaffService.save(staff));
	}

	@Operation(summary = "编辑配送员")
	@SysLog("编辑配送员")
	@SaCheckPermission("delivery:staff:edit")
	@PutMapping
	public Result<Boolean> update(@RequestBody @Valid DeliveryStaffDTO dto) {
		DeliveryStaff staff = new DeliveryStaff();
		staff.setId(dto.getId());
		staff.setUserId(dto.getUserId());
		staff.setStaffName(dto.getStaffName());
		staff.setStaffPhone(dto.getStaffPhone());
		staff.setStatus(dto.getStatus());
		staff.setVehicleInfo(dto.getVehicleInfo());
		return Result.success(deliveryStaffService.updateById(staff));
	}

	@Operation(summary = "删除配送员")
	@SysLog("删除配送员")
	@SaCheckPermission("delivery:staff:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> del(@PathVariable String id) {
		return Result.success(deliveryStaffService.removeById(id));
	}

	@Operation(summary = "更新配送员状态")
	@SysLog("更新配送员状态")
	@SaCheckPermission("delivery:staff:status")
	@PutMapping("/{id}/status")
	public Result<Boolean> updateStatus(@PathVariable String id, @RequestParam String status) {
		return Result.success(deliveryStaffService.updateStatus(id, status));
	}

}