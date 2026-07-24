
package com.aryn.cloud.pay.handler;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;

/**
 * 支付抽象类
 *
 * @author 雨滴kian
 * @since 2023/4/18 19:11
 */
@Slf4j
public abstract class AbstractPayOrderHandler implements PayOrderHandler {

	@Override
	public Object pay(CreateOrderReqDTO createOrderReqDTO) {
		// 参数检查
		validateRequest(createOrderReqDTO);
		// 创建订单
		PayTradeOrder payTradeOrder = createOrder(createOrderReqDTO);
		validateOrderConsistency(createOrderReqDTO, payTradeOrder);
		if (CommonConstants.YES.equals(payTradeOrder.getPayStatus())) {
			return payTradeOrder;
		}
		// 支付逻辑
		Object result = doPay(payTradeOrder);
		// 后置处理
		postPay(payTradeOrder);
		return result;
	}

	public abstract Object doPay(PayTradeOrder payTradeOrder);

	void validateRequest(CreateOrderReqDTO request) {
		if (request == null || !StringUtils.hasText(request.getOutTradeNo())
				|| !StringUtils.hasText(request.getTradeType()) || !StringUtils.hasText(request.getSubject())
				|| !StringUtils.hasText(request.getUserId())) {
			throw new ArynBusinessException("支付请求参数不完整");
		}
		if (request.getOutTradeNo().length() > 64 || request.getSubject().length() > 64) {
			throw new ArynBusinessException("支付订单号或标题过长");
		}
		boolean supported = Arrays.stream(PayTradeTypeEnum.values())
			.anyMatch(type -> type.getName().equals(request.getTradeType()));
		if (!supported) {
			throw new ArynBusinessException("不支持的支付方式");
		}
		BigDecimal amount = parseAmount(request.getTotalAmount());
		if (PayTradeTypeEnum.FREE_PAY.getName().equals(request.getTradeType())) {
			if (amount.compareTo(BigDecimal.ZERO) != 0) {
				throw new ArynBusinessException("零元支付金额必须为0");
			}
		}
		else {
			if (amount.compareTo(new BigDecimal("0.01")) < 0
					|| amount.compareTo(new BigDecimal("99999999.99")) > 0 || amount.scale() > 2) {
				throw new ArynBusinessException("支付金额不合法");
			}
			validateHttpUrl(request.getNotifyUrl(), "支付回调地址不合法");
		}
		validateOptionalHttpUrl(request.getReturnUrl(), "支付跳转地址不合法");
		validateOptionalHttpUrl(request.getQuitUrl(), "支付退出地址不合法");
		if (request.getExtra() != null && request.getExtra().length() > 255) {
			throw new ArynBusinessException("支付扩展参数过长");
		}
	}

	private void validateOrderConsistency(CreateOrderReqDTO request, PayTradeOrder order) {
		if (order == null || !request.getOutTradeNo().equals(order.getOutTradeNo())
				|| !request.getTradeType().equals(order.getTradeType())
				|| !request.getUserId().equals(order.getUserId())
				|| order.getAmount() == null
				|| parseAmount(request.getTotalAmount()).compareTo(order.getAmount()) != 0) {
			throw new ArynBusinessException("支付订单关键字段不一致");
		}
	}

	private BigDecimal parseAmount(String amount) {
		try {
			return new BigDecimal(amount);
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("支付金额不合法");
		}
	}

	private void validateOptionalHttpUrl(String value, String message) {
		if (StringUtils.hasText(value)) {
			validateHttpUrl(value, message);
		}
	}

	private void validateHttpUrl(String value, String message) {
		if (!StringUtils.hasText(value)) {
			throw new ArynBusinessException(message);
		}
		try {
			URI uri = URI.create(value);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					|| !StringUtils.hasText(uri.getHost())) {
				throw new ArynBusinessException(message);
			}
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException(message);
		}
	}

	private void postPay(PayTradeOrder payTradeOrder) {
		// 后置处理
	}

}
