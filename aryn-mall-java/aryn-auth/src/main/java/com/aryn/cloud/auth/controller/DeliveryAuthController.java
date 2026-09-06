
package com.aryn.cloud.auth.controller;

import com.aryn.cloud.auth.service.DeliveryAuthService;
import com.aryn.cloud.common.core.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城配送员身份认证
 *
 * <p>商城 TOC token 专用：Boot 模式经 /boot/delivery/** 访问，
 * Cloud 模式经网关 /auth/delivery/** 路由至本服务。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/delivery")
@Tag(description = "delivery-auth", name = "商城配送员身份认证")
public class DeliveryAuthController {

	private final DeliveryAuthService deliveryAuthService;

	@Operation(summary = "查询配送工作台资格")
	@GetMapping("/eligibility")
	public Result<DeliveryAuthService.DeliveryEligibilityVO> eligibility() {
		return Result.success(deliveryAuthService.eligibility());
	}

	@Operation(summary = "换取配送员身份")
	@PostMapping("/exchange")
	public Result<DeliveryAuthService.DeliveryExchangeVO> exchange() {
		return Result.success(deliveryAuthService.exchange());
	}

}
