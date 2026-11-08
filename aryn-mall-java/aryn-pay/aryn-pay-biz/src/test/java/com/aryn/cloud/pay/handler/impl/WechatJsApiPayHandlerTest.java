package com.aryn.cloud.pay.handler.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatJsApiPayHandlerTest {

	private static final String TENANT = PayTestFixtures.TENANT;

	private static final String WX_OPEN_ID = "ozexS3bXeBjIkrfIZoe07SWXBY4I";

	@Mock
	private IPayTradeOrderService payTradeOrderService;

	@Mock
	private PayCallbackUrlBuilder callbackUrlBuilder;

	@Mock
	private PayCredentialResolver credentialResolver;

	@Mock
	private PayChannelClientFactory clientFactory;

	@Mock
	private RemoteMiniAppTenantService remoteMiniAppTenantService;

	private WechatJsApiPayHandler handler;

	@BeforeEach
	void setUp() {
		handler = new WechatJsApiPayHandler(payTradeOrderService, callbackUrlBuilder, credentialResolver,
				clientFactory);
		ReflectionTestUtils.setField(handler, "remoteMiniAppTenantService", remoteMiniAppTenantService);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private CreateOrderReqDTO request(String platform, String buyerId) {
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setOutTradeNo("ORDER-WX-1");
		request.setTradeType("WX_JSAPI_PAY");
		request.setSubject("测试商品");
		request.setTotalAmount("9.90");
		request.setBuyerId(buyerId);
		request.setUserId("mall-user-1");
		request.setNotifyUrl("https://api.example.com/pay/notify");
		request.setClientPlatform(platform);
		return request;
	}

	@Test
	void wechatPlatformCreatesWechatJsapiOrder() {
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-wx");
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		when(credentialResolver.resolveNewPayment("1", "0")).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(true);
		when(payTradeOrderService.save(any())).thenReturn(true);

		PayTradeOrder saved = handler.createOrder(request("WX_MA", WX_OPEN_ID));

		assertThat(saved.getOpenId()).isEqualTo(WX_OPEN_ID);
		assertThat(saved.getTradeType()).isEqualTo("WX_JSAPI_PAY");
		verify(payTradeOrderService).save(saved);
	}

	@Test
	void wechatMiniAliasIsAccepted() {
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-wx");
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		when(credentialResolver.resolveNewPayment("1", "0")).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(true);
		when(payTradeOrderService.save(any())).thenReturn(true);

		assertThat(handler.createOrder(request("WX_MINI", WX_OPEN_ID)).getTradeType()).isEqualTo("WX_JSAPI_PAY");
	}

	@Test
	void alipayPlatformCannotCreateWechatJsapiOrder() {
		when(payTradeOrderService.getOne(any())).thenReturn(null);

		assertThatThrownBy(() -> handler.createOrder(request("ALI_MINI", WX_OPEN_ID)))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付平台与微信小程序支付不匹配");
		verify(payTradeOrderService, never()).save(any());
	}

	@Test
	void missingPlatformCannotCreateWechatJsapiOrder() {
		when(payTradeOrderService.getOne(any())).thenReturn(null);

		assertThatThrownBy(() -> handler.createOrder(request(null, WX_OPEN_ID)))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("缺少客户端平台，不能发起小程序支付");
	}

	@Test
	void alipayBuyerIdentityCannotBeWrittenToWechatPayer() {
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-wx");
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		when(credentialResolver.resolveNewPayment("1", "0")).thenReturn(identity);

		assertThatThrownBy(() -> handler.createOrder(request("WX_MA", "2088123456789012")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("微信小程序买家身份不能使用支付宝身份");
		verify(remoteMiniAppTenantService, never()).verifyBuyerIdentity(any());
		verify(payTradeOrderService, never()).save(any());
	}

	@Test
	void wechatBuyerMustBelongToMallUser() {
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-wx");
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		when(credentialResolver.resolveNewPayment("1", "0")).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(false);

		assertThatThrownBy(() -> handler.createOrder(request("WX_MA", WX_OPEN_ID)))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("微信小程序买家身份与当前商城用户不匹配");
		verify(payTradeOrderService, never()).save(any());
	}

	@Test
	void existingWechatOrderStillRejectsAlipayPlatformOnPrepay() {
		PayTradeOrder existing = new PayTradeOrder().setOutTradeNo("ORDER-WX-1").setTradeType("WX_JSAPI_PAY")
			.setUserId("mall-user-1").setOpenId(WX_OPEN_ID).setAmount(new BigDecimal("9.90"))
			.setConfigRevisionId("rev-wx");
		when(payTradeOrderService.getOne(any())).thenReturn(existing);

		assertThatThrownBy(() -> handler.createOrder(request("ALI_MINI", WX_OPEN_ID)))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付平台与微信小程序支付不匹配");
	}

}
