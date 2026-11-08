package com.aryn.cloud.pay.dubbo;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.handler.PayOrderHandler;
import com.aryn.cloud.pay.service.IPayNotifyRecordService;
import com.aryn.cloud.pay.service.PayCapabilityService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RemotePayServiceImplTest {

	@Test
	void unknownTradeTypeCannotBeInvokedEvenIfHandlerBeanExists() {
		PayOrderHandler handler = mock(PayOrderHandler.class);
		RemotePayServiceImpl service = new RemotePayServiceImpl(Map.of("ALI_UNKNOWN_PAY", handler),
				mock(IPayNotifyRecordService.class), mock(PayCapabilityService.class));
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setTradeType("ALI_UNKNOWN_PAY");

		ArynBusinessException exception = assertThrows(ArynBusinessException.class, () -> service.createOrder(request));

		assertEquals("不支持的支付方式", exception.getMsg());
		verify(handler, never()).pay(request);
	}

}
