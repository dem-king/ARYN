package com.aryn.cloud.pay.service.impl;

import com.alipay.api.AlipayApiErrorEnum;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayEventOutboxService;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayNotifyAck;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.pay.service.PayTradeTransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayNotifyRecordServiceImplTest {

	private PayEventOutboxService outboxService;

	private PayTradeOrderMapper tradeOrderMapper;

	private PayRefundOrderMapper refundOrderMapper;

	private HttpServletRequest request;

	private PayCredentialResolver credentialResolver;

	private PayTradeTransactionService tradeTransactionService;

	private PayChannelClientFactory clientFactory;

	private AlipayClient alipayClient;

	private TestPayNotifyRecordService service;

	private PayResolvedIdentity identity;

	private PayConfigRevision revision;

	@BeforeEach
	void setUp() {
		outboxService = mock(PayEventOutboxService.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		refundOrderMapper = mock(PayRefundOrderMapper.class);
		request = mock(HttpServletRequest.class);
		credentialResolver = mock(PayCredentialResolver.class);
		tradeTransactionService = mock(PayTradeTransactionService.class);
		clientFactory = mock(PayChannelClientFactory.class);
		alipayClient = mock(AlipayClient.class);
		identity = PayTestFixtures.alipayIdentity("rev-1");
		revision = PayTestFixtures.revision("rev-1", "config-2", "2", "2", "ali-synthetic-app",
				"2088000000000000", "{}");
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		service = new TestPayNotifyRecordService(outboxService, tradeOrderMapper, refundOrderMapper, request,
				mock(IPayConfigService.class), credentialResolver, clientFactory, tradeTransactionService,
				new PayMaterialParser());
		ReflectionTestUtils.setField(service, "baseMapper", mock(PayNotifyRecordMapper.class));
		when(credentialResolver.resolveForSignature(PayTestFixtures.TENANT, "rev-1")).thenReturn(List.of(identity));
		when(credentialResolver.resolveForOrder(anyString(), any())).thenReturn(identity);
		when(credentialResolver.revision(PayTestFixtures.TENANT, "rev-1")).thenReturn(revision);
		when(credentialResolver.sameIdentity(any(), any())).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void alipaySignatureFalseIsRejectedBeforeOrderMutation() {
		service.signatureValid = false;
		stubRequest(alipayParams());

		PayNotifyAck ack = service.aliPayNotifyV2(PayTestFixtures.TENANT, "rev-1", request);

		assertFalse(ack.success());
		verify(tradeOrderMapper, never()).selectOne(any());
		verify(tradeOrderMapper, never()).markPaidIfPending(anyString(), anyString(), anyString(), any());
	}

	@Test
	void alipayBusinessFieldsMustMatchFrozenMerchantAndStoredAmount() {
		PayTradeOrder order = order();
		Map<String, String> params = alipayParams();
		assertTrue(service.validAlipayNotification(params, order, identity));

		params.put("app_id", "attacker-app");
		assertFalse(service.validAlipayNotification(params, order, identity));
		params.put("app_id", "ali-synthetic-app");
		params.put("total_amount", "0.01");
		assertFalse(service.validAlipayNotification(params, order, identity));
		params.put("total_amount", "99.90");
		PayResolvedIdentity missingMerchant = PayResolvedIdentity.builder()
			.tenantId(identity.getTenantId())
			.configId(identity.getConfigId())
			.revisionId(identity.getRevisionId())
			.channel(identity.getChannel())
			.terminalType(identity.getTerminalType())
			.appId(identity.getAppId())
			.mchId(null)
			.environment(identity.getEnvironment())
			.materials(identity.getMaterials())
			.fingerprints(identity.getFingerprints())
			.build();
		assertFalse(service.validAlipayNotification(params, order, missingMerchant));
	}

	@Test
	void alipayMiniCallbackBuyerMustMatchFrozenIdentity() {
		PayResolvedIdentity userIdIdentity = PayTestFixtures.alipayMiniIdentity("rev-1", "user_id");
		PayTradeOrder miniOrder = order().setTradeType("ALI_JSAPI_PAY").setOpenId("2088123456789012");
		Map<String, String> params = alipayParams();
		params.put("app_id", "2021000000000001");
		assertTrue(service.validAlipayNotification(params, miniOrder, userIdIdentity));

		params.put("buyer_id", "2088123456789012");
		assertTrue(service.validAlipayNotification(params, miniOrder, userIdIdentity));
		params.put("buyer_id", "2088123456789999");
		assertFalse(service.validAlipayNotification(params, miniOrder, userIdIdentity));
		params.remove("buyer_id");
		// 渠道未回传买家字段时仍以验签/商户/金额为准
		assertTrue(service.validAlipayNotification(params, miniOrder, userIdIdentity));

		PayResolvedIdentity openIdIdentity = PayTestFixtures.alipayMiniIdentity("rev-1", "open_id");
		PayTradeOrder openIdOrder = order().setTradeType("ALI_JSAPI_PAY")
			.setOpenId("074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5");
		params.put("buyer_id", "2088123456789012");
		params.put("buyer_open_id", "074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo5");
		assertTrue(service.validAlipayNotification(params, openIdOrder, openIdIdentity));
		params.put("buyer_open_id", "074a1CcTG1LelxKe4xQC0zgNdId0nxi95b5lsNpazWYoCo6");
		assertFalse(service.validAlipayNotification(params, openIdOrder, openIdIdentity));
	}

	@Test
	void alipayMiniQueryBuyerMismatchDoesNotSettle() {
		PayTradeOrder miniOrder = order().setTradeType("ALI_JSAPI_PAY").setOpenId("2088123456789012");
		when(tradeOrderMapper.selectOne(any())).thenReturn(miniOrder);
		PayResolvedIdentity userIdIdentity = PayTestFixtures.alipayMiniIdentity("rev-1", "user_id");
		when(credentialResolver.resolveForOrder(anyString(), any())).thenReturn(userIdIdentity);
		AlipayTradeQueryResponse query = alipayQuerySuccess();
		query.setBuyerUserId("2088123456789999");
		service.alipayQueryResult = query;

		PaySettlementResult result = service.queryAndSettlePayOrder(PayTestFixtures.TENANT, "ORDER-1");

		assertFalse(result.isPaid());
		verify(tradeTransactionService, never()).settleAlipayQuery(anyString(), any(), any());
	}

	@Test
	void wechatBusinessFieldsMustMatchFrozenMerchantAndStoredAmount() {
		PayResolvedIdentity wechat = PayTestFixtures.wechatIdentity("rev-wx");
		WxPayNotifyV3Result.DecryptNotifyResult result = new WxPayNotifyV3Result.DecryptNotifyResult();
		result.setAppid("wx-synthetic-app");
		result.setMchid("synthetic-mch");
		result.setTradeState("SUCCESS");
		result.setTransactionId("WX-1");
		result.setSuccessTime("2026-07-23T12:00:00+08:00");
		WxPayNotifyV3Result.Amount amount = new WxPayNotifyV3Result.Amount();
		amount.setTotal(9990);
		amount.setCurrency(PayConstants.CURRENCY);
		result.setAmount(amount);

		assertTrue(service.validWxPayNotification(result, order(), wechat));
		amount.setTotal(1);
		assertFalse(service.validWxPayNotification(result, order(), wechat));
	}

	@Test
	void sameChannelDuplicateIsAcknowledgedWithoutSendingAnotherMessage() {
		service.signatureValid = true;
		stubRequest(alipayParams());
		PayTradeOrder pending = order();
		when(tradeOrderMapper.selectOne(any())).thenReturn(pending);
		when(tradeOrderMapper.markPaidIfPending(PayTestFixtures.TENANT, "pay-1", "ALI-1",
				pending.getPaySuccessTime())).thenReturn(0);
		PayTradeOrder paid = order().setPayStatus("1").setChannelOrderNo("ALI-1");
		when(tradeOrderMapper.selectById("pay-1")).thenReturn(paid);

		PayNotifyAck ack = service.aliPayNotifyV2(PayTestFixtures.TENANT, "rev-1", request);

		assertTrue(ack.success());
		verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void successfulAlipayNotificationRequiresSuccessfulMessageDelivery() {
		service.signatureValid = true;
		stubRequest(alipayParams());
		when(tradeOrderMapper.selectOne(any())).thenReturn(order());
		when(tradeOrderMapper.markPaidIfPending(PayTestFixtures.TENANT, "pay-1", "ALI-1",
				java.time.LocalDateTime.of(2026, 7, 23, 12, 0))).thenReturn(1);

		PayNotifyAck ack = service.aliPayNotifyV2(PayTestFixtures.TENANT, "rev-1", request);

		assertTrue(ack.success());
		verify(outboxService).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void alipayQuerySettlesPaidOrderThroughSamePath() {
		PayTradeOrder alipayOrder = order().setTradeType("ALI_H5_PAY");
		when(tradeOrderMapper.selectOne(any())).thenReturn(alipayOrder);
		when(tradeTransactionService.settleAlipayQuery(eq(PayTestFixtures.TENANT), eq(alipayOrder), any()))
			.thenReturn(PaySettlementResult.paid("ALI-1"));
		service.alipayQueryResult = alipayQuerySuccess();

		PaySettlementResult result = service.queryAndSettlePayOrder(PayTestFixtures.TENANT, "ORDER-1");

		assertTrue(result.isPaid());
		verify(tradeTransactionService).settleAlipayQuery(eq(PayTestFixtures.TENANT), eq(alipayOrder), any());
	}

	@Test
	void alipayQueryUsesCertificateExecute() throws Exception {
		PayTradeOrder alipayOrder = order().setTradeType("ALI_H5_PAY");
		when(tradeOrderMapper.selectOne(any())).thenReturn(alipayOrder);
		when(tradeTransactionService.settleAlipayQuery(eq(PayTestFixtures.TENANT), eq(alipayOrder), any()))
			.thenReturn(PaySettlementResult.paid("ALI-1"));
		stubCertificateResponses(alipayQuerySuccess(), closeSuccess());
		service.realAlipayQuery = true;

		PaySettlementResult result = service.queryAndSettlePayOrder(PayTestFixtures.TENANT, "ORDER-1");

		assertTrue(result.isPaid());
		verify(alipayClient).certificateExecute(any());
		verify(alipayClient, never()).execute(any());
	}

	@Test
	void alipayCloseUsesCertificateExecuteForQueryAndClose() throws Exception {
		PayTradeOrder alipayOrder = order().setTradeType("ALI_H5_PAY");
		when(tradeOrderMapper.selectOne(any())).thenReturn(alipayOrder);
		when(tradeTransactionService.claimClose(PayTestFixtures.TENANT, "pay-1"))
			.thenReturn(new PayTradeTransactionService.CloseClaim(
					PayTradeTransactionService.CloseDecision.CLAIMED, null));
		stubCertificateResponses(alipayQueryPending(), closeSuccess());
		service.realAlipayQuery = true;

		PayCloseResult result = service.closeAndCheckPayOrder("ORDER-1");

		assertTrue(PayCloseResult.STATUS_CLOSED.equals(result.getStatus()));
		verify(alipayClient, times(2)).certificateExecute(any());
		verify(alipayClient, never()).execute(any());
		verify(tradeTransactionService).markClosed(PayTestFixtures.TENANT, "pay-1");
	}

	@Test
	void callbackForDifferentMerchantIdentityIsRejected() {
		service.signatureValid = true;
		stubRequest(alipayParams());
		when(tradeOrderMapper.selectOne(any())).thenReturn(order());
		when(credentialResolver.sameIdentity(any(), any())).thenReturn(false);

		PayNotifyAck ack = service.aliPayNotifyV2(PayTestFixtures.TENANT, "rev-1", request);

		assertFalse(ack.success());
		verify(tradeOrderMapper, never()).markPaidIfPending(anyString(), anyString(), anyString(), any());
	}

	private void stubRequest(Map<String, String> params) {
		Map<String, String[]> servletParams = new LinkedHashMap<>();
		params.forEach((key, value) -> servletParams.put(key, new String[] { value }));
		when(request.getParameterMap()).thenReturn(servletParams);
	}

	private Map<String, String> alipayParams() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("app_id", "ali-synthetic-app");
		params.put("seller_id", "2088000000000000");
		params.put("out_trade_no", "ORDER-1");
		params.put("trade_no", "ALI-1");
		params.put("trade_status", "TRADE_SUCCESS");
		params.put("total_amount", "99.90");
		params.put("gmt_payment", "2026-07-23 12:00:00");
		params.put("sign", "signature");
		return params;
	}

	private AlipayTradeQueryResponse alipayQuerySuccess() {
		AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
		response.setCode("10000");
		response.setTradeStatus("TRADE_SUCCESS");
		response.setOutTradeNo("ORDER-1");
		response.setTotalAmount("99.90");
		response.setTradeNo("ALI-1");
		response.setSendPayDate(java.util.Date.from(
				java.time.LocalDateTime.of(2026, 7, 23, 12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant()));
		return response;
	}

	private AlipayTradeQueryResponse alipayQueryPending() {
		AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
		response.setCode("10000");
		response.setTradeStatus("WAIT_BUYER_PAY");
		response.setOutTradeNo("ORDER-1");
		response.setTotalAmount("99.90");
		return response;
	}

	private AlipayTradeCloseResponse closeSuccess() {
		AlipayTradeCloseResponse response = new AlipayTradeCloseResponse();
		response.setCode("10000");
		return response;
	}

	private void stubCertificateResponses(AlipayTradeQueryResponse query,
			AlipayTradeCloseResponse close) throws AlipayApiException {
		when(clientFactory.alipayClient(identity)).thenReturn(alipayClient);
		lenient().doThrow(new AlipayApiException(AlipayApiErrorEnum.CERT_EXECUTE_ERROR))
			.when(alipayClient).execute(any());
		doAnswer(invocation -> {
			Object request = invocation.getArgument(0);
			if (request instanceof AlipayTradeQueryRequest) {
				return query;
			}
			if (request instanceof AlipayTradeCloseRequest) {
				return close;
			}
			throw new IllegalStateException("unexpected alipay request");
		}).when(alipayClient).certificateExecute(any());
	}

	private PayTradeOrder order() {
		return new PayTradeOrder().setId("pay-1").setTenantId(PayTestFixtures.TENANT).setOutTradeNo("ORDER-1")
			.setTerminalType("2").setTradeType("ALI_H5_PAY").setPayStatus("0")
			.setAmount(new BigDecimal("99.90")).setExtra("{\"payType\":\"2\"}")
			.setConfigRevisionId("rev-1")
			.setPaySuccessTime(java.time.LocalDateTime.of(2026, 7, 23, 12, 0));
	}

	private static final class TestPayNotifyRecordService extends PayNotifyRecordServiceImpl {

		private boolean signatureValid;

		private TestPayNotifyRecordService(PayEventOutboxService outboxService,
				PayTradeOrderMapper payTradeOrderMapper, PayRefundOrderMapper payRefundOrderMapper,
				HttpServletRequest httpServletRequest, IPayConfigService payConfigService,
				PayCredentialResolver credentialResolver, PayChannelClientFactory clientFactory,
				PayTradeTransactionService tradeTransactionService, PayMaterialParser materialParser) {
			super(outboxService, payTradeOrderMapper, payRefundOrderMapper, httpServletRequest, payConfigService,
					credentialResolver, clientFactory, tradeTransactionService, materialParser);
		}

		private AlipayTradeQueryResponse alipayQueryResult;

		private boolean realAlipayQuery;

		@Override
		protected boolean verifyAlipaySignature(Map<String, String> params, byte[] alipayPublicCert)
				throws AlipayApiException {
			return signatureValid;
		}

		@Override
		protected AlipayTradeQueryResponse queryAlipayOrder(PayResolvedIdentity identity, String outTradeNo)
				throws AlipayApiException {
			return realAlipayQuery ? super.queryAlipayOrder(identity, outTradeNo) : alipayQueryResult;
		}
	}

}
