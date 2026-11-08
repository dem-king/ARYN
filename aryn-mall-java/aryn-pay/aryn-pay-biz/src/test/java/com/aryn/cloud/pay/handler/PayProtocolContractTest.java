package com.aryn.cloud.pay.handler;

import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayInvokeTypeEnum;
import com.aryn.cloud.pay.config.PayCallbackProperties;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.config.PayClientIpProperties;
import com.aryn.cloud.pay.handler.impl.AlipayAppPayHandler;
import com.aryn.cloud.pay.handler.impl.AlipayH5PayHandler;
import com.aryn.cloud.pay.handler.impl.WechatAppPayHandler;
import com.aryn.cloud.pay.handler.impl.WechatH5PayHandler;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayClientIpResolver;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 终端协议契约：只验证返回结构与调起方式，不调用真实渠道。
 */
class PayProtocolContractTest {

	private PayCredentialResolver credentialResolver;

	private PayChannelClientFactory clientFactory;

	private PayCallbackUrlBuilder callbackUrlBuilder;

	private PayTradeOrder order;

	@BeforeEach
	void setUp() {
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		credentialResolver = mock(PayCredentialResolver.class);
		clientFactory = mock(PayChannelClientFactory.class);
		CallbackPrefixProperties prefix = new CallbackPrefixProperties();
		prefix.setPay("pay");
		callbackUrlBuilder = new PayCallbackUrlBuilder(prefix, new PayCallbackProperties());
		order = new PayTradeOrder().setId("pay-1")
			.setOutTradeNo("ORDER-1")
			.setDescription("协议测试")
			.setAmount(new BigDecimal("99.90"))
			.setNotifyUrl("https://shop.example.com/cb")
			.setConfigRevisionId("rev-1")
			.setTenantId(PayTestFixtures.TENANT)
			.setTerminalType("1");
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void wechatAppReturnsObjectParamsNotJsonString() throws Exception {
		PayResolvedIdentityHolder identity = new PayResolvedIdentityHolder();
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity.wechat);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity.wechat)).thenReturn(wxPayService);
		WxPayUnifiedOrderV3Result.AppResult appResult = new WxPayUnifiedOrderV3Result.AppResult();
		appResult.setAppid("wx-synthetic-app");
		appResult.setPartnerId("synthetic-mch");
		appResult.setPrepayId("prepay-1");
		appResult.setPackageValue("Sign=WXPay");
		appResult.setNoncestr("nonce-1");
		appResult.setTimestamp("1700000000");
		appResult.setSign("sign-1");
		when(wxPayService.createOrderV3(eq(TradeTypeEnum.APP), any(WxPayUnifiedOrderV3Request.class)))
			.thenReturn(appResult);

		WechatAppPayHandler handler = new WechatAppPayHandler(mock(IPayTradeOrderService.class),
				callbackUrlBuilder, credentialResolver, clientFactory);
		PayPrepayResult result = handler.doPay(order);

		assertEquals(PayInvokeTypeEnum.WX_APP, result.getInvokeType());
		assertEquals("wx-synthetic-app", result.getParams().get("appid"));
		assertEquals("synthetic-mch", result.getParams().get("partnerid"));
		assertEquals("prepay-1", result.getParams().get("prepayid"));
		assertEquals("Sign=WXPay", result.getParams().get("package"));
		assertEquals("sign-1", result.getParams().get("sign"));
		assertTrue(result.getParams().get("sign") instanceof String);
	}

	@Test
	void wechatH5UsesRealRemoteIpAndRedirectUrl() throws Exception {
		PayResolvedIdentityHolder identity = new PayResolvedIdentityHolder();
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity.wechat);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity.wechat)).thenReturn(wxPayService);
		WxPayUnifiedOrderV3Result h5Result = new WxPayUnifiedOrderV3Result();
		h5Result.setH5Url("https://wx.example.com/h5");
		when(wxPayService.createOrderV3(eq(TradeTypeEnum.H5), any(WxPayUnifiedOrderV3Request.class)))
			.thenReturn(h5Result);
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getRemoteAddr()).thenReturn("203.0.113.10");
		PayClientIpProperties ipProperties = new PayClientIpProperties();
		WechatH5PayHandler handler = new WechatH5PayHandler(mock(IPayTradeOrderService.class),
				callbackUrlBuilder, credentialResolver, clientFactory,
				new PayClientIpResolver(ipProperties), request);

		PayPrepayResult result = handler.doPay(order);

		assertEquals(PayInvokeTypeEnum.REDIRECT, result.getInvokeType());
		assertEquals("https://wx.example.com/h5", result.getParams().get("redirectUrl"));
		ArgumentCaptor<WxPayUnifiedOrderV3Request> captor = ArgumentCaptor
			.forClass(WxPayUnifiedOrderV3Request.class);
		verify(wxPayService).createOrderV3(eq(TradeTypeEnum.H5), captor.capture());
		assertEquals("203.0.113.10", captor.getValue().getSceneInfo().getPayerClientIp());
		assertEquals("Wap", captor.getValue().getSceneInfo().getH5Info().getType());
	}

	@Test
	void alipayAppReturnsSignedOrderString() throws Exception {
		PayResolvedIdentityHolder identity = new PayResolvedIdentityHolder();
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity.alipay);
		AlipayClient alipayClient = mock(AlipayClient.class);
		when(clientFactory.alipayClient(identity.alipay)).thenReturn(alipayClient);
		AlipayTradeAppPayResponse response = new AlipayTradeAppPayResponse();
		response.setBody("alipay-signed-order");
		when(alipayClient.sdkExecute(any(AlipayTradeAppPayRequest.class))).thenReturn(response);
		AlipayAppPayHandler handler = new AlipayAppPayHandler(mock(IPayTradeOrderService.class),
				callbackUrlBuilder, credentialResolver, clientFactory);

		PayPrepayResult result = handler.doPay(order);

		assertEquals(PayInvokeTypeEnum.ALI_APP, result.getInvokeType());
		assertEquals("alipay-signed-order", result.getParams().get("orderString"));
	}

	@Test
	void alipayH5UsesGetPageExecuteUrl() throws Exception {
		PayResolvedIdentityHolder identity = new PayResolvedIdentityHolder();
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity.alipay);
		AlipayClient alipayClient = mock(AlipayClient.class);
		when(clientFactory.alipayClient(identity.alipay)).thenReturn(alipayClient);
		AlipayTradeWapPayResponse response = new AlipayTradeWapPayResponse();
		response.setBody("https://openapi.alipay.com/gateway.do?sign=1");
		when(alipayClient.pageExecute(any(AlipayTradeWapPayRequest.class), eq("GET"))).thenReturn(response);
		AlipayH5PayHandler handler = new AlipayH5PayHandler(mock(IPayTradeOrderService.class),
				callbackUrlBuilder, credentialResolver, clientFactory);

		PayPrepayResult result = handler.doPay(order);

		assertEquals(PayInvokeTypeEnum.REDIRECT, result.getInvokeType());
		assertEquals("https://openapi.alipay.com/gateway.do?sign=1", result.getParams().get("redirectUrl"));
	}

	private static final class PayResolvedIdentityHolder {

		private final com.aryn.cloud.pay.service.PayResolvedIdentity wechat = PayTestFixtures
			.wechatIdentity("rev-1");

		private final com.aryn.cloud.pay.service.PayResolvedIdentity alipay = PayTestFixtures
			.alipayIdentity("rev-1");

	}

}
