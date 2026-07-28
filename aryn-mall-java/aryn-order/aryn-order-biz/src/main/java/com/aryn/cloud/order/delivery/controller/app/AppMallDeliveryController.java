package com.aryn.cloud.order.delivery.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.delivery.vo.DeliveryTaskCustomerVO;
import com.aryn.cloud.order.delivery.service.CustomerDeliveryService;
import com.aryn.cloud.upms.api.vo.MaterialAccessVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 客户侧商城配送公开进度。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery/app/tasks")
@Tag(name = "客户商城配送进度")
public class AppMallDeliveryController {

	private final CustomerDeliveryService customerDeliveryService;

	@GetMapping("/order/{orderId}")
	@Operation(summary = "查询本人订单的商城配送进度")
	public Result<DeliveryTaskCustomerVO> getByOrder(@PathVariable String orderId) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(customerDeliveryService.getByOrder(orderId));
	}

	@GetMapping("/order/{orderId}/evidences/{evidenceId}/access")
	@Operation(summary = "获取本人订单配送凭证短期地址")
	public Result<MaterialAccessVO> getEvidenceAccess(@PathVariable String orderId,
			@PathVariable String evidenceId) {
		SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(customerDeliveryService.getEvidenceAccess(orderId, evidenceId));
	}
}
