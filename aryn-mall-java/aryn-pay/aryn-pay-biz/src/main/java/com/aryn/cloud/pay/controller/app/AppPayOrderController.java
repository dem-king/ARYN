
package com.aryn.cloud.pay.controller.app;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
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

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/app/order")
@RequiredArgsConstructor
@Tag(description = "order", name = "支付订单")
public class AppPayOrderController {

	private final IPayTradeOrderService payTradeOrderService;

	@Operation(summary = "通过订单号查询")
	@GetMapping("/{orderNo}")
	public Result getOrder(@PathVariable("orderNo") String orderNo) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		PayTradeOrder order = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, orderNo)
			.eq(PayTradeOrder::getUserId, user.getUserId()));
		if (order == null) {
			return Result.success(null);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("outTradeNo", order.getOutTradeNo());
		result.put("payStatus", order.getPayStatus());
		result.put("amount", order.getAmount());
		result.put("extra", order.getExtra());
		return Result.success(result);
	}

}
