
package com.aryn.cloud.order.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderRefundEnum;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
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

	private final OrderStaffNotifyService orderStaffNotifyService;

	@Operation(summary = "退款单列表")
	@GetMapping("/page")
	public Result<IPage<OrderRefund>> page(Page page, OrderRefund orderRefund) {
		orderRefund.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(orderRefundService.getPage(page, orderRefund));
	}

	@Operation(summary = "退款单详情")
	@GetMapping("/{id}")
	public Result<OrderRefund> getById(@PathVariable String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderRefundService.getUserRefundById(id, userId));
	}

	/**
	 * 申请退货退款。
	 *
	 * <p>通知挂在这里而不是 saveRefund：拼团成团失败的整单自动退款也复用 saveRefund，
	 * 若在服务层发通知，会自动退款也报「待审核」误报。只有买家在本入口提交的申请
	 * 才真的等待审核。saveRefund 返回的正是落库后的实体（含单号与状态）。
	 */
	@Operation(summary = "申请退货退款")
	@PostMapping
	public Result<OrderRefund> saveRefund(@RequestBody OrderRefund orderRefund) {
		orderRefund.setUserId(SecurityUtils.getUser().getUserId());
		OrderRefund saved = orderRefundService.saveRefund(orderRefund);
		if (saved != null && OrderRefundEnum.PENDING_REVIEW.getCode().equals(saved.getStatus())) {
			try {
				orderStaffNotifyService.notifyRefundApplied(saved);
			}
			catch (RuntimeException exception) {
				log.warn("退款申请通知发送失败，不影响申请本身 refundId={}: {}", saved.getId(), exception.getMessage());
			}
		}
		return Result.success(saved);
	}

}
