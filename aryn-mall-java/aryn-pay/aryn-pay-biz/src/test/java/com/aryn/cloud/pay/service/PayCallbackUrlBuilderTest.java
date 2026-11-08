package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.config.PayCallbackProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayCallbackUrlBuilderTest {

	private final PayCallbackUrlBuilder builder = builder(false, false);

	@Test
	void buildsIdentityVersionedCallbackRoutes() {
		assertEquals("https://shop.example.com/cb/pay/notify/v2/wx/tenant-1/revision-1",
				builder.buildWxPayNotifyUrl("https://shop.example.com/cb", "tenant-1", "revision-1"));
		assertEquals("https://shop.example.com/cb/pay/notify/v2/refunds/wx/tenant-1/revision-1",
				builder.buildWxRefundNotifyUrl("https://shop.example.com/cb", "tenant-1", "revision-1"));
		assertEquals("https://shop.example.com/cb/pay/notify/v2/alipay/tenant-1/revision-1",
				builder.buildAliPayNotifyUrl("https://shop.example.com/cb", "tenant-1", "revision-1"));
	}

	@Test
	void rejectsHttpByDefault() {
		assertThrows(ArynBusinessException.class,
				() -> builder.buildWxPayNotifyUrl("http://shop.example.com", "tenant-1", "revision-1"));
	}

	@Test
	void rejectsUserInfoQueryFragmentAndLocalhost() {
		assertThrows(ArynBusinessException.class, () -> builder.validateBase("https://user:pass@shop.example.com"));
		assertThrows(ArynBusinessException.class, () -> builder.validateBase("https://shop.example.com?a=1"));
		assertThrows(ArynBusinessException.class, () -> builder.validateBase("https://shop.example.com#frag"));
		assertThrows(ArynBusinessException.class, () -> builder.validateBase("https://localhost:9999"));
		assertThrows(ArynBusinessException.class, () -> builder.validateBase("https://127.0.0.1:9999"));
	}

	@Test
	void explicitTestEnvironmentCanAllowHttpAndPrivateHost() {
		PayCallbackUrlBuilder testBuilder = builder(true, true);

		assertEquals("http://localhost:8080/pay/notify/v2/wx/t/r",
				testBuilder.buildWxPayNotifyUrl("http://localhost:8080", "t", "r"));
	}

	private PayCallbackUrlBuilder builder(boolean allowHttp, boolean allowPrivate) {
		CallbackPrefixProperties prefix = new CallbackPrefixProperties();
		prefix.setPay("pay");
		PayCallbackProperties properties = new PayCallbackProperties();
		properties.setAllowHttp(allowHttp);
		properties.setAllowPrivate(allowPrivate);
		return new PayCallbackUrlBuilder(prefix, properties);
	}

}
