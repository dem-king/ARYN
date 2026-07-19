
package com.aryn.cloud.order.controller.app;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Maps;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.MallCommonConstants;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.dto.PrepayDTO;
import com.aryn.cloud.order.api.dto.SettlementOrderDTO;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderRefundEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:01
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/orderinfo")
@Tag(description = "app-orderinfo", name = "商城订单-API")
public class AppOrderInfoController {

	private final IOrderInfoService orderInfoService;

	private final IOrderRefundService orderRefundService;

	@Operation(summary = "订单列表")
	@GetMapping("/page")
	public Result<IPage<OrderInfo>> page(Page page, OrderInfo orderInfo) {
		orderInfo.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(orderInfoService.apiPage(page, orderInfo));
	}

	@Operation(summary = "通过订单id查询")
	@GetMapping("/{id}")
	public Result<OrderInfo> getById(@PathVariable String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.getUserOrderById(id, userId));
	}

	@Operation(summary = "结算订单")
	@PostMapping("/settlement")
	public Result<OrderInfo> settlementOrder(@Valid @RequestBody SettlementOrderDTO settlementOrderDTO) {
		settlementOrderDTO.setUserId(SecurityUtils.getUser().getUserId());
		return Result.success(orderInfoService.settlementOrder(settlementOrderDTO));
	}

	@Operation(summary = "创建订单")
	@PostMapping("/create")
	public Result<OrderInfo> createOrder(HttpServletRequest request, @Valid @RequestBody CreateOrderDTO createOrderDTO) {
		createOrderDTO.setUserId(SecurityUtils.getUser().getUserId());
		createOrderDTO.setOpenId(SecurityUtils.getOpenId());
		createOrderDTO.setAppId(request.getHeader(MallCommonConstants.HEADER_APP_ID));
		return Result.success(orderInfoService.createOrder(createOrderDTO));
	}

	@Operation(summary = "预支付")
	@PostMapping("/prepay")
	public Result<Object> prepay(@Valid @RequestBody PrepayDTO prepayDTO) {
		prepayDTO.setUserId(SecurityUtils.getUser().getUserId());
		return orderInfoService.prepay(prepayDTO);
	}

	@Operation(summary = "订单取消")
	@GetMapping("/cancel/{id}")
	public Result<String> cancelOrder(@PathVariable String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.cancelUserOrder(id, userId));
	}

	@Operation(summary = "订单删除")
	@GetMapping("/del/{id}")
	public Result<Boolean> delOrder(@PathVariable String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.deleteUserOrder(id, userId));
	}

	@Operation(summary = "订单确认收货")
	@GetMapping("/receiver/{id}")
	public Result<Boolean> receiverOrder(@PathVariable String id) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.receiveUserOrder(id, userId));
	}

	@Operation(summary = "订单评价")
	@PostMapping("/appraise/{id}")
	public Result<Boolean> appraiseOrder(@PathVariable String id,
			@Valid @RequestBody List<@Valid OrderAppraiseDTO> orderAppraiseList) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.appraiseOrder(id, userId, orderAppraiseList));

	}

	@Operation(summary = "订单数量查询")
	@GetMapping("/count")
	public Result<Map<String, Long>> count(OrderInfo orderInfo) {
		orderInfo.setUserId(SecurityUtils.getUser().getUserId());
		Map<String, Long> maps = Maps.newHashMap();
		// 待付款
		maps.put(OrderStatusEnum.WAITING_FOR_PAYMENT.getCode(),
				orderInfoService.count(Wrappers.lambdaQuery(orderInfo)
					.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_PAYMENT.getCode())
					.eq(OrderInfo::getPayStatus, CommonConstants.NO)));
		// 待发货
		maps.put(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode(), orderInfoService.count(Wrappers.lambdaQuery(orderInfo)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode())));
		// 待收货
		maps.put(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(), orderInfoService.count(Wrappers.lambdaQuery(orderInfo)
			.eq(OrderInfo::getStatus, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())));
		// 待评价
		maps.put(OrderStatusEnum.COMPLETED.getCode(),
				orderInfoService.count(Wrappers.lambdaQuery(orderInfo)
					.eq(OrderInfo::getStatus, OrderStatusEnum.COMPLETED.getCode())
					.eq(OrderInfo::getAppraiseStatus, CommonConstants.NO)));
		// 退款/售后
		OrderRefund orderRefund = new OrderRefund();
		orderRefund.setUserId(orderInfo.getUserId());
		maps.put(OrderStatusEnum.REFUNDING.getCode(),
				orderRefundService.count(Wrappers.lambdaQuery(orderRefund)
					.in(OrderRefund::getStatus, OrderRefundEnum.PENDING_REVIEW.getCode(),
							OrderRefundEnum.REFUNDING.getCode())));
		return Result.success(maps);
	}

	@Operation(summary = "通过订单编号查询")
	@GetMapping("/getByOrderNo/{orderNo}")
	public Result<OrderInfo> getOrderByOrderNo(@PathVariable String orderNo) {
		String userId = SecurityUtils.getUser().getUserId();
		return Result.success(orderInfoService.getUserOrderByOrderNo(orderNo, userId));
	}

}
