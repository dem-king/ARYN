package com.aryn.cloud.pay.handler;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.handler.impl.FreePayHandler;
import com.aryn.cloud.pay.service.PayUrlPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentHandlerValidationTest {

	@Test
	void rejectsNegativePaymentAmount() {
		TestPayHandler handler = new TestPayHandler();
		CreateOrderReqDTO request = validRequest();
		request.setTotalAmount("-1.00");

		assertThrows(ArynBusinessException.class, () -> handler.pay(request));
	}

	@Test
	void paidOrderIsReturnedWithoutCreatingAnotherChannelPayment() {
		TestPayHandler handler = new TestPayHandler();
		PayTradeOrder paidOrder = new PayTradeOrder().setOutTradeNo("ORDER-1").setTradeType("FREE_PAY")
			.setUserId("user-1").setAmount(java.math.BigDecimal.ZERO).setPayStatus(CommonConstants.YES);
		handler.order = paidOrder;

		Object result = handler.pay(validRequest());

		assertTrue(result instanceof PayPrepayResult);
		assertTrue(((PayPrepayResult) result).isAlreadyPaid());
		assertEquals("ORDER-1", ((PayPrepayResult) result).getOrderNo());
		assertEquals(0, handler.doPayCalls);
	}

	@Test
	void freePaymentTransactionStartsAtExternalHandlerEntryPoint() throws NoSuchMethodException {
		assertTrue(FreePayHandler.class.getMethod("pay", CreateOrderReqDTO.class)
			.isAnnotationPresent(Transactional.class));
	}

	@Test
	void paidOrderWithoutFrozenIdentityCannotStartChannelPayment() {
		TestPayHandler handler = new TestPayHandler();
		handler.order = new PayTradeOrder().setOutTradeNo("ORDER-1").setTradeType("WX_JSAPI_PAY")
			.setUserId("user-1").setAmount(java.math.BigDecimal.ONE).setPayStatus(CommonConstants.NO);
		CreateOrderReqDTO request = validRequest();
		request.setTradeType("WX_JSAPI_PAY");
		request.setTotalAmount("1.00");
		request.setNotifyUrl("https://example.com/pay/notify");

		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () -> handler.pay(request));
		assertEquals("支付订单缺少支付身份版本", exception.getMsg());
		assertEquals(0, handler.doPayCalls);
	}

	private CreateOrderReqDTO validRequest() {
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setOutTradeNo("ORDER-1");
		request.setTradeType("FREE_PAY");
		request.setSubject("商城购物");
		request.setTotalAmount("0.00");
		request.setUserId("user-1");
		return request;
	}

	private static final class TestPayHandler extends AbstractPayOrderHandler {

		private PayTradeOrder order = new PayTradeOrder().setOutTradeNo("ORDER-1").setTradeType("FREE_PAY")
			.setUserId("user-1").setAmount(java.math.BigDecimal.ZERO).setPayStatus(CommonConstants.NO);

		private int doPayCalls;

		private TestPayHandler() {
			this.payUrlPolicy = new PayUrlPolicy();
		}

		@Override
		public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
			doPayCalls++;
			return PayPrepayResult.alreadyPaid(payTradeOrder.getOutTradeNo());
		}

		@Override
		public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
			return order;
		}
	}

}
