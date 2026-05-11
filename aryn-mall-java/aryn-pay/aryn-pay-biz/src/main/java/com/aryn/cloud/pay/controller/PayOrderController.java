
package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
@Tag(description = "pay", name = "支付订单")
public class PayOrderController {

	private final IPayTradeOrderService payTradeOrderService;

	@Operation(summary = "通过id查询")
	@SaCheckPermission("pay:paytradeorder:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(payTradeOrderService.getById(id));
	}

	@Operation(summary = "分页查询")
	@SaCheckPermission("pay:paytradeorder:page")
	@GetMapping("/page")
	public Result getPage(Page page, PayTradeOrder payTradeOrder) {
		return Result.success(payTradeOrderService.page(page, Wrappers.query(payTradeOrder)));
	}

}
