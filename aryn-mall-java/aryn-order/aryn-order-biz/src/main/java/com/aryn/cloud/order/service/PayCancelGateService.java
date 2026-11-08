package com.aryn.cloud.order.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 取消订单前的支付门禁：挂起订单事务，先查单/关单，再决定能否释放库存与优惠。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayCancelGateService {

	private final RemotePayService remotePayService;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void assertCancellable(OrderInfo orderInfo) {
		if (orderInfo == null || !StringUtils.hasText(orderInfo.getOrderNo())) {
			return;
		}
		if (MallOrderConstants.PAYMENT_TYPE_3.equals(orderInfo.getPaymentType())) {
			// 货到付款不经过在线支付单
			return;
		}
		if (CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			throw new ArynBusinessException("订单已支付，不能取消；请走退款/售后");
		}
		boolean payOrCancelLocked = CommonConstants.YES.equals(orderInfo.getPaySubmitting())
				|| "2".equals(orderInfo.getPaySubmitting());
		if (payOrCancelLocked && orderInfo.getPaySubmitTime() != null
				&& orderInfo.getPaySubmitTime().isAfter(java.time.LocalDateTime.now().minusMinutes(2))) {
			throw new ArynBusinessException("订单正在发起支付或取消，暂不能取消，请稍后重试");
		}
		PayCloseResult result;
		try {
			result = remotePayService.closeAndCheckPayOrder(orderInfo.getOrderNo());
		}
		catch (Exception exception) {
			log.warn("取消订单前支付门禁查询失败, orderNo: {}, msg: {}", orderInfo.getOrderNo(),
					exception.getMessage());
			throw new ArynBusinessException("支付状态确认失败，暂不能取消，请稍后重试");
		}
		if (result == null) {
			throw new ArynBusinessException("支付状态确认失败，暂不能取消，请稍后重试");
		}
		if (result.paid()) {
			throw new ArynBusinessException("订单已支付，不能取消；请走退款/售后");
		}
		if (!result.closedOrNotInitiated()) {
			throw new ArynBusinessException("支付结果确认中，暂不能取消，请稍后重试");
		}
	}

}
