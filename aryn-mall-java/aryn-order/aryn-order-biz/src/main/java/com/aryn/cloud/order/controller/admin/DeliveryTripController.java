
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.entity.DeliveryTrip;
import com.aryn.cloud.order.service.IDeliveryTripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 出车单管理
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/trip")
@Tag(description = "delivery-trip", name = "出车单管理")
public class DeliveryTripController {

	private final IDeliveryTripService deliveryTripService;

	@Operation(summary = "出车单分页列表")
	@SaCheckPermission("delivery:trip:page")
	@GetMapping("/page")
	public Result<IPage<DeliveryTrip>> page(Page page, DeliveryTrip deliveryTrip) {
		return Result.success(deliveryTripService.page(page,
				com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(deliveryTrip)
						.orderByDesc(DeliveryTrip::getCreateTime)));
	}

	@Operation(summary = "出车单详情")
	@SaCheckPermission("delivery:trip:get")
	@GetMapping("/{id}")
	public Result<DeliveryTrip> getById(@PathVariable String id) {
		return Result.success(deliveryTripService.getTripDetail(id));
	}

}