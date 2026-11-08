package com.aryn.cloud.user.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 充值订单取消前的支付门禁：挂起充值取消事务，远程查单/关单在事务外执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeCancelGateService {

	private final RemotePayService remotePayService;

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public void assertCancellable(String orderNo) {
		PayCloseResult result;
		try {
			result = remotePayService.closeAndCheckPayOrder(orderNo);
		}
		catch (Exception exception) {
			log.warn("充值订单取消前支付门禁查询失败, orderNo: {}, msg: {}", orderNo, exception.getMessage());
			throw new ArynBusinessException("支付状态确认失败，暂不能取消充值订单");
		}
		if (result == null) {
			throw new ArynBusinessException("支付状态确认失败，暂不能取消充值订单");
		}
		if (result.paid()) {
			throw new ArynBusinessException("充值订单已支付，不能取消");
		}
		if (!result.closedOrNotInitiated()) {
			throw new ArynBusinessException("支付结果确认中，暂不能取消充值订单");
		}
	}

}
