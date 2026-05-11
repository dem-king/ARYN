
package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.service.IOrderRefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商城退款单
 *
 * @author 雨滴kian
 * @date 2022/5/31
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/orderrefund")
@Tag(description = "orderrefund", name = "商城退款单")
public class OrderRefundController {

	private final IOrderRefundService orderRefundService;

	@Operation(summary = "退款单列表")
	@SaCheckPermission("order:orderrefund:page")
	@GetMapping("/page")
	public Result<IPage<OrderRefund>> page(Page page, OrderRefund orderRefund) {
		return Result.success(orderRefundService.getPage(page, orderRefund));
	}

	@Operation(summary = "退款单查询")
	@SaCheckPermission("order:orderrefund:get")
	@GetMapping("/{id}")
	public Result<OrderRefund> page(@PathVariable("id") String id) {
		return Result.success(orderRefundService.getRefundById(id));
	}

	@SysLog("退货退款")
	@Operation(summary = "退货退款")
	@SaCheckPermission("order:orderrefund:refund")
	@PostMapping("/refund")
	public Result<Boolean> refund(@RequestBody OrderRefund orderRefund) {
		return Result.success(orderRefundService.refund(orderRefund));
	}

	@Operation(summary = "数量查询")
	@GetMapping("/count")
	public Result getCount() {
		long allCount = orderRefundService.count(Wrappers.lambdaQuery());
		LocalDateTime localDateTime = LocalDateTime.now();
		long todayCount = orderRefundService.count(Wrappers.<OrderRefund>lambdaQuery()
			.ge(OrderRefund::getCreateTime, LocalDateTimeUtil.beginOfDay(localDateTime))
			.le(OrderRefund::getCreateTime, LocalDateTimeUtil.endOfDay(localDateTime)));
		Map<String, Object> rt = new HashMap<>();
		// 今日数量
		rt.put("todayCount", todayCount);
		// 全部数量
		rt.put("allCount", allCount);
		return Result.success(rt);
	}

}
