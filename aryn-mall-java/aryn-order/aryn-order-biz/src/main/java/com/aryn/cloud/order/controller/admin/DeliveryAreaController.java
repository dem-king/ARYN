
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.entity.DeliveryArea;
import com.aryn.cloud.order.service.IDeliveryAreaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 配送范围管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/area")
@Tag(description = "delivery-area", name = "配送范围管理")
public class DeliveryAreaController {

	private final IDeliveryAreaService deliveryAreaService;

	@Operation(summary = "配送范围分页列表")
	@SaCheckPermission("delivery:area:page")
	@GetMapping("/page")
	public Result<IPage<DeliveryArea>> page(Page page, DeliveryArea deliveryArea) {
		return Result.success(deliveryAreaService.page(page,
				com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(deliveryArea)
						.orderByAsc(DeliveryArea::getProvinceCode)
						.orderByAsc(DeliveryArea::getCityCode)
						.orderByAsc(DeliveryArea::getAreaCode)));
	}

	@Operation(summary = "新增配送范围")
	@SysLog("新增配送范围")
	@SaCheckPermission("delivery:area:add")
	@PostMapping
	public Result<Boolean> save(@RequestBody DeliveryArea deliveryArea) {
		return Result.success(deliveryAreaService.save(deliveryArea));
	}

	@Operation(summary = "修改配送范围")
	@SysLog("修改配送范围")
	@SaCheckPermission("delivery:area:edit")
	@PutMapping
	public Result<Boolean> update(@RequestBody DeliveryArea deliveryArea) {
		return Result.success(deliveryAreaService.updateById(deliveryArea));
	}

	@Operation(summary = "逻辑删除配送范围")
	@SysLog("删除配送范围")
	@SaCheckPermission("delivery:area:del")
	@DeleteMapping("/{id}")
	public Result<Boolean> remove(@PathVariable String id) {
		return Result.success(deliveryAreaService.removeById(id));
	}

}