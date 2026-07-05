
package com.aryn.cloud.order.controller.app;

import cn.hutool.core.util.ObjectUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 子订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:01
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/orderitem")
@Tag(description = "app-orderitem", name = "商城子订单-API")
public class AppOrderItemController {

	private final IOrderItemService orderItemService;

	private final IOrderInfoService orderInfoService;

	@Operation(summary = "通过订单项id查询")
	@GetMapping("/{id}")
	public Result<OrderItemEntity> getById(@PathVariable String id) {
		OrderItemEntity orderItem = orderItemService.getOrderItemById(id);
		if (ObjectUtil.isNull(orderItem)) {
			return Result.fail("订单项不存在");
		}
		// 通过 orderId 校验订单归属
		OrderInfo orderInfo = orderInfoService.getById(orderItem.getOrderId());
		if (ObjectUtil.isNull(orderInfo) || !orderInfo.getUserId().equals(SecurityUtils.getUser().getUserId())) {
			return Result.fail("无权操作该订单");
		}
		return Result.success(orderItem);
	}

}
