package com.aryn.cloud.user.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import com.aryn.cloud.user.service.IRechargeOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 充值订单管理
 *
 * @author 雨滴kian
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/rechargeorder")
@Tag(description = "rechargeorder", name = "充值订单管理")
public class RechargeOrderController {

	private final IRechargeOrderService rechargeOrderService;

	@Operation(summary = "充值订单分页列表")
	@SaCheckPermission("user:rechargeorder:page")
	@GetMapping("/page")
	public Result page(Page page, RechargeOrder rechargeOrder) {
		return Result.success(rechargeOrderService.getPage(page, rechargeOrder));
	}

	@Operation(summary = "充值订单查询")
	@SaCheckPermission("user:rechargeorder:get")
	@GetMapping("/{id}")
	public Result getById(@PathVariable("id") String id) {
		return Result.success(rechargeOrderService.getById(id));
	}

}
