package com.aryn.cloud.promotion.controller.app;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import com.aryn.cloud.promotion.service.ISeckillScheduleService;
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

	private final ISeckillScheduleService seckillScheduleService;

	@Operation(summary = "秒杀场次列表（今日剩余 + 明日预告）")
	@GetMapping("/sessions")
	public Result getSessions() {
		// 公开接口：未登录也能逛会场；登录态存在时顺带回填提醒订阅状态
		String mallUserId = null;
		try {
			if (StpUtil.isLogin()) {
				mallUserId = StpUtil.getLoginIdAsString();
			}
		}
		catch (Exception ignored) {
			// 无 token / token 失效：按游客处理，不阻断会场
		}
		return Result.success(seckillActivityService.getActiveSessions(mallUserId));
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
	@Operation(summary = "订阅场次开抢提醒")
	@PostMapping("/subscribe/{sessionId}")
	public Result subscribe(@PathVariable("sessionId") String sessionId) {
		seckillScheduleService.subscribe(sessionId, SecurityUtils.getUser().getUserId());
		return Result.success(true);
	}

	@SaCheckLogin
	@Operation(summary = "取消场次开抢提醒")
	@DeleteMapping("/subscribe/{sessionId}")
	public Result unsubscribe(@PathVariable("sessionId") String sessionId) {
		seckillScheduleService.unsubscribe(sessionId, SecurityUtils.getUser().getUserId());
		return Result.success(true);
	}

	@SaCheckLogin
	@Operation(summary = "查询场次开抢提醒订阅状态")
	@GetMapping("/subscribe/{sessionId}")
	public Result getSubscribeStatus(@PathVariable("sessionId") String sessionId) {
		return Result.success(seckillScheduleService.isSubscribed(
				sessionId, SecurityUtils.getUser().getUserId()));
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
