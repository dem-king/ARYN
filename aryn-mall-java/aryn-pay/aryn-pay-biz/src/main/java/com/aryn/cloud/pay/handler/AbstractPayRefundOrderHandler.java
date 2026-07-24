
package com.aryn.cloud.pay.handler;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 支付抽象类
 *
 * @author 雨滴kian
 * @since 2023/4/18 19:11
 */
@Slf4j
public abstract class AbstractPayRefundOrderHandler implements PayRefundOrderHandler {

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Object refund(CreateRefundsReqDTO createRefundsReqDTO) {
		// 参数检查
		validateRequest(createRefundsReqDTO);
		// 创建订单
		PayRefundOrder payRefundOrder = createRefundOrder(createRefundsReqDTO);
		validateRefundConsistency(createRefundsReqDTO, payRefundOrder);
		if (PayRefundOrderStatusEnum.STATUS_2.getCode().equals(payRefundOrder.getRefundStatus())) {
			return payRefundOrder;
		}
		// 退款逻辑
		Object result = doRefund(payRefundOrder);
		// 后置处理
		postPay(payRefundOrder);
		return result;
	}

	public abstract Object doRefund(PayRefundOrder payRefundOrder);

	void validateRequest(CreateRefundsReqDTO request) {
		if (request == null || !StringUtils.hasText(request.getOutTradeNo())
				|| !StringUtils.hasText(request.getRefundTradeNo())
				|| !StringUtils.hasText(request.getRefundType()) || !StringUtils.hasText(request.getUserId())
				|| request.getTotalAmount() == null || request.getRefundAmount() == null) {
			throw new ArynBusinessException("退款请求参数不完整");
		}
		if (!Set.of("WECHAT_REFUND", "ALIPAY_REFUND", "FREE_REFUND").contains(request.getRefundType())) {
			throw new ArynBusinessException("不支持的退款方式");
		}
		BigDecimal totalAmount = request.getTotalAmount();
		BigDecimal refundAmount = request.getRefundAmount();
		boolean freeRefund = "FREE_REFUND".equals(request.getRefundType());
		if (freeRefund && (totalAmount.compareTo(BigDecimal.ZERO) != 0 || refundAmount.compareTo(BigDecimal.ZERO) != 0)) {
			throw new ArynBusinessException("零元退款金额必须为0");
		}
		if (!freeRefund && (totalAmount.compareTo(BigDecimal.ZERO) <= 0 || refundAmount.compareTo(BigDecimal.ZERO) <= 0
				|| refundAmount.compareTo(totalAmount) > 0 || totalAmount.scale() > 2 || refundAmount.scale() > 2)) {
			throw new ArynBusinessException("退款金额不合法");
		}
		if (request.getOutTradeNo().length() > 64 || request.getRefundTradeNo().length() > 64
				|| request.getExtra() != null && request.getExtra().length() > 255) {
			throw new ArynBusinessException("退款请求参数过长");
		}
	}

	private void validateRefundConsistency(CreateRefundsReqDTO request, PayRefundOrder order) {
		if (order == null || order.getPayAmount() == null || order.getRefundAmount() == null
				|| !request.getOutTradeNo().equals(order.getOutTradeNo())
				|| !request.getRefundTradeNo().equals(order.getRefundTradeNo())
				|| !request.getUserId().equals(order.getUserId())
				|| request.getTotalAmount().compareTo(order.getPayAmount()) != 0
				|| request.getRefundAmount().compareTo(order.getRefundAmount()) != 0) {
			throw new ArynBusinessException("退款订单关键字段不一致");
		}
	}

	private void postPay(PayRefundOrder payRefundOrder) {
		// 后置处理
	}

}
