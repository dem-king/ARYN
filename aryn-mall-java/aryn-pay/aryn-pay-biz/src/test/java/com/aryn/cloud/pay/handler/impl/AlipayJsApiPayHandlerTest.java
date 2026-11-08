package com.aryn.cloud.pay.handler.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiErrorEnum;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayRequest;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayInvokeTypeEnum;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayJsApiPayHandlerTest {

	private static final String TENANT = PayTestFixtures.TENANT;

	private static final String USER_ID = "2088123456789012";

	private static final String OPEN_ID = "074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5";

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

	@Mock
	private AlipayClient alipayClient;

	private AlipayJsApiPayHandler handler;

	@BeforeEach
	void setUp() {
		handler = new AlipayJsApiPayHandler(payTradeOrderService, callbackUrlBuilder, credentialResolver,
				clientFactory);
		ReflectionTestUtils.setField(handler, "remoteMiniAppTenantService", remoteMiniAppTenantService);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private PayTradeOrder order(String buyerIdentity, String userId) {
		return new PayTradeOrder().setOutTradeNo("ORDER-1").setTenantId(TENANT).setDescription("测试商品")
			.setOpenId(buyerIdentity).setUserId(userId).setAmount(new BigDecimal("9.90"))
			.setConfigRevisionId("rev-3").setNotifyUrl("https://api.example.com/pay/notify");
	}

	private AlipayTradeCreateResponse createResponse(String tradeNo) {
		AlipayTradeCreateResponse response = new AlipayTradeCreateResponse();
		response.setCode("10000");
		response.setTradeNo(tradeNo);
		return response;
	}

	private void stubChannel(PayResolvedIdentity identity, String tradeNo) throws Exception {
		when(callbackUrlBuilder.buildAliPayNotifyUrl(any(), any(), any()))
			.thenReturn("https://api.example.com/pay/notify");
		when(clientFactory.alipayClient(identity)).thenReturn(alipayClient);
		// 模拟证书模式：普通 execute 必失败，只有 certificateExecute 可用
		lenient().doThrow(new AlipayApiException(AlipayApiErrorEnum.CERT_EXECUTE_ERROR))
			.when(alipayClient)
			.execute(org.mockito.ArgumentMatchers.<AlipayRequest<AlipayTradeCreateResponse>>any());
		doReturn(createResponse(tradeNo)).when(alipayClient)
			.certificateExecute(org.mockito.ArgumentMatchers.<AlipayRequest<AlipayTradeCreateResponse>>any());
	}

	@Test
	void userIdModeSendsBuyerIdOnly() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "user_id");
		PayTradeOrder order = order(USER_ID, "mall-user-1");
		when(credentialResolver.resolveForOrder(TENANT, order)).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(true);
		stubChannel(identity, "2026110700000001");

		PayPrepayResult result = handler.doPay(order);

		ArgumentCaptor<AlipayTradeCreateRequest> captor = ArgumentCaptor.forClass(AlipayTradeCreateRequest.class);
		verify(alipayClient).certificateExecute(captor.capture());
		verify(alipayClient, never()).execute(any());
		JSONObject biz = JSON.parseObject(captor.getValue().getBizContent());
		assertThat(biz.getString("buyer_id")).isEqualTo(USER_ID);
		assertThat(biz.containsKey("buyer_open_id")).isFalse();
		assertThat(result.getInvokeType()).isEqualTo(PayInvokeTypeEnum.ALI_MINI);
		assertThat(result.getParams()).containsEntry("tradeNo", "2026110700000001");
	}

	@Test
	void openIdModeSendsBuyerOpenIdOnly() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "open_id");
		PayTradeOrder order = order(OPEN_ID, "mall-user-1");
		when(credentialResolver.resolveForOrder(TENANT, order)).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(true);
		stubChannel(identity, "2026110700000002");

		PayPrepayResult result = handler.doPay(order);

		ArgumentCaptor<AlipayTradeCreateRequest> captor = ArgumentCaptor.forClass(AlipayTradeCreateRequest.class);
		verify(alipayClient).certificateExecute(captor.capture());
		verify(alipayClient, never()).execute(any());
		JSONObject biz = JSON.parseObject(captor.getValue().getBizContent());
		assertThat(biz.getString("buyer_open_id")).isEqualTo(OPEN_ID);
		assertThat(biz.containsKey("buyer_id")).isFalse();
		assertThat(result.getParams()).containsEntry("tradeNo", "2026110700000002");
	}

	@Test
	void wechatOpenIdCannotBeUsedAsAlipayBuyer() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "open_id");
		PayTradeOrder order = order("ozexS3bXeBjIkrfIZoe07SWXBY4I", "mall-user-1");
		when(credentialResolver.resolveForOrder(TENANT, order)).thenReturn(identity);

		assertThatThrownBy(() -> handler.doPay(order))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝买家 open_id 格式不合法");
		verify(remoteMiniAppTenantService, never()).verifyBuyerIdentity(any());
		verify(alipayClient, never()).execute(any());
	}

	@Test
	void buyerIdentityMustBelongToMallUser() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "user_id");
		PayTradeOrder order = order(USER_ID, "mall-user-1");
		when(credentialResolver.resolveForOrder(TENANT, order)).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(false);

		assertThatThrownBy(() -> handler.doPay(order))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝买家身份与当前商城用户不匹配");
		verify(alipayClient, never()).execute(any());
	}

	@Test
	void missingBuyerModeBlocksPayment() {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", null);
		PayTradeOrder order = order(USER_ID, "mall-user-1");
		when(credentialResolver.resolveForOrder(TENANT, order)).thenReturn(identity);

		assertThatThrownBy(() -> handler.doPay(order))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序买家身份模式未配置");
	}

	@Test
	void createOrderValidatesBuyerBeforeSaving() {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "user_id");
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		when(credentialResolver.resolveNewPayment("2", "0")).thenReturn(identity);
		when(remoteMiniAppTenantService.verifyBuyerIdentity(any())).thenReturn(true);
		when(payTradeOrderService.save(any())).thenReturn(true);
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setOutTradeNo("ORDER-2");
		request.setTradeType("ALI_JSAPI_PAY");
		request.setSubject("测试商品");
		request.setTotalAmount("9.90");
		request.setBuyerId(USER_ID);
		request.setUserId("mall-user-1");
		request.setNotifyUrl("https://api.example.com/pay/notify");
		request.setClientPlatform("ALI_MINI");

		PayTradeOrder saved = handler.createOrder(request);

		assertThat(saved.getOpenId()).isEqualTo(USER_ID);
		assertThat(saved.getConfigRevisionId()).isEqualTo("rev-3");
		assertThat(saved.getTradeType()).isEqualTo("ALI_JSAPI_PAY");
		verify(payTradeOrderService).save(saved);
	}

	@Test
	void createOrderRejectsWechatBuyerBeforeSaving() {
		PayResolvedIdentity identity = PayTestFixtures.alipayMiniIdentity("rev-3", "open_id");
		when(credentialResolver.resolveNewPayment("2", "0")).thenReturn(identity);
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setOutTradeNo("ORDER-3");
		request.setTradeType("ALI_JSAPI_PAY");
		request.setSubject("测试商品");
		request.setTotalAmount("9.90");
		request.setBuyerId("ozexS3bXeBjIkrfIZoe07SWXBY4I");
		request.setUserId("mall-user-1");
		request.setNotifyUrl("https://api.example.com/pay/notify");
		request.setClientPlatform("ALI_MINI");

		assertThatThrownBy(() -> handler.createOrder(request))
			.isInstanceOf(ArynBusinessException.class);
		verify(payTradeOrderService, never()).save(any());
	}

	@Test
	void createOrderRejectsWechatPlatform() {
		when(payTradeOrderService.getOne(any())).thenReturn(null);
		CreateOrderReqDTO request = new CreateOrderReqDTO();
		request.setOutTradeNo("ORDER-4");
		request.setTradeType("ALI_JSAPI_PAY");
		request.setSubject("测试商品");
		request.setTotalAmount("9.90");
		request.setBuyerId(USER_ID);
		request.setUserId("mall-user-1");
		request.setNotifyUrl("https://api.example.com/pay/notify");
		request.setClientPlatform("WX_MA");

		assertThatThrownBy(() -> handler.createOrder(request))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付平台与支付宝小程序支付不匹配");
		verify(remoteMiniAppTenantService, never()).verifyBuyerIdentity(any());
		verify(payTradeOrderService, never()).save(any());
	}

}
