
package com.aryn.cloud.order.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.service.IOrderRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/orderrefund")
@Tag(description = "app-orderrefund", name = "商城退款单-API")
public class AppOrderRefundController {

	private final IOrderRefundService orderRefundService;

	@Operation(summary = "退款单列表")
	@GetMapping("/page")
	public Result<IPage<OrderRefund>> page(Page page, OrderRefund orderRefund) {
		orderRefund.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(orderRefundService.getPage(page, orderRefund));
	}

	@Operation(summary = "退款单详情")
	@GetMapping("/{id}")
	public Result<OrderRefund> getById(@PathVariable String id) {
		return Result.success(orderRefundService.getRefundById(id));
	}

	@Operation(summary = "申请退货退款")
	@PostMapping
	public Result<OrderRefund> saveRefund(@RequestBody OrderRefund orderRefund) {
		orderRefund.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(orderRefundService.saveRefund(orderRefund));
	}

}
