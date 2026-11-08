package com.aryn.cloud.pay.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.pay.api.vo.PayCapabilityVO;
import com.aryn.cloud.pay.service.PayCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端支付能力：只下发已交付、已启用、材料合格且当前终端可调起的渠道。
 */
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
@Tag(description = "pay-capability", name = "支付能力")
public class PayCapabilityController {

	private final PayCapabilityService capabilityService;

	@Operation(summary = "当前租户可用支付能力")
	@GetMapping("/capability")
	public Result<List<PayCapabilityVO>> capability(
			@RequestParam(value = "terminalType", required = false) String terminalType,
			@RequestParam(value = "platformType", required = false) String platformParam,
			@RequestHeader(value = "platform-type", required = false) String platformHeader) {
		String platformType = StringUtils.hasText(platformParam) ? platformParam : platformHeader;
		return Result.success(capabilityService.query(terminalType, platformType));
	}

}
