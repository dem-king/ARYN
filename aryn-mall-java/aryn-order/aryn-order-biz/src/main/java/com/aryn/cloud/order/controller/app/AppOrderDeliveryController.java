
package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.vo.DeliveryProgressVO;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 客户端配送进度
 *
 * @author aryn
 * @since 2025/7/31
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/order")
@Tag(description = "app-order-delivery", name = "客户端配送进度-API")
public class AppOrderDeliveryController {

	private final IDeliveryTaskService deliveryTaskService;

	@Operation(summary = "配送进度时间线")
	@GetMapping("/{orderId}/delivery-progress")
	public Result<DeliveryProgressVO> deliveryProgress(@PathVariable String orderId) {
		return Result.success(deliveryTaskService.getProgress(orderId));
	}

}
