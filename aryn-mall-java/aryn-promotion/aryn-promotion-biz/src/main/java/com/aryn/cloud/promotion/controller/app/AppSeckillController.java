package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/seckill")
@Tag(description = "app-seckill", name = "C端秒杀")
public class AppSeckillController {

	private final ISeckillActivityService seckillActivityService;

	private final ISeckillOrderService seckillOrderService;

	@Operation(summary = "秒杀场次列表")
	@GetMapping("/sessions")
	public Result getSessions() {
		return Result.success(seckillActivityService.getActiveSessions());
	}

	@Operation(summary = "场次商品列表")
	@GetMapping("/sessions/{sessionId}/goods")
	public Result getSessionGoods(@PathVariable("sessionId") String sessionId) {
		return Result.success(seckillActivityService.getSessionGoods(sessionId));
	}

	@Operation(summary = "商品秒杀信息")
	@GetMapping("/goods/{skuId}")
	public Result getGoodsSeckillInfo(@PathVariable("skuId") String skuId) {
		return Result.success(seckillActivityService.getGoodsSeckillInfo(skuId));
	}

	@SaCheckLogin
	@Operation(summary = "秒杀下单(预扣库存)")
	@PostMapping("/order")
	public Result createOrder(@Valid @RequestBody SeckillOrderDTO dto) {
		String userId = SecurityUtils.getUser().getUserId();
		String orderId = StpUtil.getTokenValue();
		return Result.success(seckillOrderService.createSeckillOrder(dto, userId, orderId));
	}

	@SaCheckLogin
	@Operation(summary = "取消秒杀订单(回滚预扣库存)")
	@DeleteMapping("/order/{orderId}")
	public Result cancelOrder(@PathVariable("orderId") String orderId) {
		return Result.success(seckillOrderService.rollbackByOrderId(orderId));
	}
}
