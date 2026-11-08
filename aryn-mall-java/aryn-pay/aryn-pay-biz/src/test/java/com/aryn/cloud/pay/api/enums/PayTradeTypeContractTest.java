package com.aryn.cloud.pay.api.enums;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 支付场景契约：终端、调起方式与是否已交付必须固化，P1/P2 只能在此基础上补实现。
 */
class PayTradeTypeContractTest {

	@Test
	void tradeTypeCodesAreUnique() {
		Set<String> codes = Arrays.stream(PayTradeTypeEnum.values())
			.map(PayTradeTypeEnum::getName)
			.collect(Collectors.toSet());

		assertEquals(PayTradeTypeEnum.values().length, codes.size());
	}

	@Test
	void miniProgramAndAppAndH5KeepDistinctProtocols() {
		assertContract(PayTradeTypeEnum.WX_JSAPI_PAY, PayChannelEnum.WECHAT, PayTerminalTypeEnum.MINI_PROGRAM,
				PayInvokeTypeEnum.WX_MINI, true);
		assertContract(PayTradeTypeEnum.WX_APP_PAY, PayChannelEnum.WECHAT, PayTerminalTypeEnum.APP,
				PayInvokeTypeEnum.WX_APP, true);
		assertContract(PayTradeTypeEnum.WX_H5_PAY, PayChannelEnum.WECHAT, PayTerminalTypeEnum.H5,
				PayInvokeTypeEnum.REDIRECT, true);
		assertContract(PayTradeTypeEnum.ALI_APP_PAY, PayChannelEnum.ALIPAY, PayTerminalTypeEnum.APP,
				PayInvokeTypeEnum.ALI_APP, true);
		assertContract(PayTradeTypeEnum.ALI_H5_PAY, PayChannelEnum.ALIPAY, PayTerminalTypeEnum.H5,
				PayInvokeTypeEnum.REDIRECT, true);
		assertContract(PayTradeTypeEnum.ALI_JSAPI_PAY, PayChannelEnum.ALIPAY, PayTerminalTypeEnum.MINI_PROGRAM,
				PayInvokeTypeEnum.ALI_MINI, true);
	}

	@Test
	void freePayHasNoChannelInvocation() {
		assertContract(PayTradeTypeEnum.FREE_PAY, PayChannelEnum.FREE, null, PayInvokeTypeEnum.NONE, true);
		assertNull(PayTradeTypeEnum.FREE_PAY.getTerminal());
	}

	@Test
	void alipayMiniProtocolIsDelivered() {
		assertTrue(PayTradeTypeEnum.ALI_JSAPI_PAY.isImplemented());
	}

	private void assertContract(PayTradeTypeEnum tradeType, PayChannelEnum channel, PayTerminalTypeEnum terminal,
			PayInvokeTypeEnum invokeType, boolean implemented) {
		assertEquals(channel, tradeType.getChannel());
		assertEquals(terminal, tradeType.getTerminal());
		assertEquals(invokeType, tradeType.getInvokeType());
		assertEquals(implemented, tradeType.isImplemented());
		assertTrue(tradeType.getName().equals(tradeType.name()));
	}

}
