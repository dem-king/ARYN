package com.aryn.cloud.pay.service.impl;

import com.alipay.api.AlipayApiErrorEnum;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayEventOutboxService;
import com.aryn.cloud.pay.service.PayEventOutboxTxService;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.pay.service.PayTradeTransactionService;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1.1 事务边界证据：渠道查询/关单执行时不得处于支付域数据库事务中。
 */
class PayTransactionBoundaryTest {

	private PayEventOutboxService outboxService;

	private PayTradeOrderMapper tradeOrderMapper;

	private PayCredentialResolver credentialResolver;

	private PayChannelClientFactory clientFactory;

	private PayTradeTransactionService tradeTransactionService;

	private PayNotifyRecordServiceImpl service;

	@BeforeEach
	void setUp() {
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		outboxService = mock(PayEventOutboxService.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		credentialResolver = mock(PayCredentialResolver.class);
		clientFactory = mock(PayChannelClientFactory.class);
		tradeTransactionService = mock(PayTradeTransactionService.class);
		service = new PayNotifyRecordServiceImpl(outboxService, tradeOrderMapper, mock(PayRefundOrderMapper.class),
				mock(HttpServletRequest.class), mock(IPayConfigService.class), credentialResolver, clientFactory,
				tradeTransactionService, new PayMaterialParser());
		ReflectionTestUtils.setField(service, "baseMapper", mock(PayNotifyRecordMapper.class));
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void queryChannelRunsWithoutActiveDatabaseTransaction() throws Exception {
		PayTradeOrder order = order("PENDING", "WX_JSAPI_PAY", "0");
		when(tradeOrderMapper.selectOne(any())).thenReturn(order);
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-1");
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity)).thenReturn(wxPayService);
		doAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"微信查单必须在事务外执行");
			return wxQuery("SUCCESS");
		}).when(wxPayService).queryOrderV3(any(), anyString());
		when(tradeTransactionService.settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class)))
			.thenReturn(PaySettlementResult.paid("WX-1"));

		PaySettlementResult result = service.queryAndSettlePayOrder(PayTestFixtures.TENANT, "ORDER-1");

		assertTrue(result.isPaid());
	}

	@Test
	void wxCloseTimeoutRunsOutsideTransactionAndStaysUnknown() throws Exception {
		PayTradeOrder order = order("PENDING", "WX_JSAPI_PAY", "0");
		when(tradeOrderMapper.selectOne(any())).thenReturn(order);
		when(tradeTransactionService.claimClose(PayTestFixtures.TENANT, "pay-1"))
			.thenReturn(new PayTradeTransactionService.CloseClaim(
					PayTradeTransactionService.CloseDecision.CLAIMED, null));
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-1");
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity)).thenReturn(wxPayService);
		doAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"微信关单前查单必须在事务外执行");
			return wxQuery("NOTPAY");
		}).when(wxPayService).queryOrderV3(any(), anyString());
		doAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"微信关单必须在事务外执行");
			throw new WxPayException("timeout");
		}).when(wxPayService).closeOrderV3("ORDER-1");

		PayCloseResult result = service.closeAndCheckPayOrder("ORDER-1");

		assertEquals(PayCloseResult.STATUS_UNKNOWN, result.getStatus());
		verify(tradeTransactionService).markUnknown(eq(PayTestFixtures.TENANT), eq("pay-1"), any(), anyString());
	}

	@Test
	void alipayCloseTimeoutRunsOutsideTransactionAndStaysUnknown() throws Exception {
		PayTradeOrder order = order("PENDING", "ALI_H5_PAY", "0");
		when(tradeOrderMapper.selectOne(any())).thenReturn(order);
		when(tradeTransactionService.claimClose(PayTestFixtures.TENANT, "pay-1"))
			.thenReturn(new PayTradeTransactionService.CloseClaim(
					PayTradeTransactionService.CloseDecision.CLAIMED, null));
		PayResolvedIdentity identity = PayTestFixtures.alipayIdentity("rev-1");
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity);
		AlipayClient alipayClient = mock(AlipayClient.class);
		when(clientFactory.alipayClient(identity)).thenReturn(alipayClient);
		// 证书模式：普通 execute 必失败，正确路径必须走 certificateExecute
		lenient().doThrow(new AlipayApiException(AlipayApiErrorEnum.CERT_EXECUTE_ERROR))
			.when(alipayClient).execute(any());
		doAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"支付宝查单必须在事务外执行");
			AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
			response.setCode("10000");
			response.setTradeStatus("WAIT_BUYER_PAY");
			response.setOutTradeNo("ORDER-1");
			return response;
		}).when(alipayClient).certificateExecute(any(AlipayTradeQueryRequest.class));
		doAnswer(invocation -> {
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
					"支付宝关单必须在事务外执行");
			throw new AlipayApiException("timeout");
		}).when(alipayClient).certificateExecute(any(AlipayTradeCloseRequest.class));

		PayCloseResult result = service.closeAndCheckPayOrder("ORDER-1");

		assertEquals(PayCloseResult.STATUS_UNKNOWN, result.getStatus());
		verify(alipayClient, never()).execute(any());
		verify(tradeTransactionService).markUnknown(eq(PayTestFixtures.TENANT), eq("pay-1"), any(), anyString());
	}

	@Test
	void wxQueryPaidMovesToPaidWithoutClosingChannel() throws Exception {
		PayTradeOrder order = order("CLOSING", "WX_JSAPI_PAY", "0");
		when(tradeOrderMapper.selectOne(any())).thenReturn(order);
		when(tradeTransactionService.claimClose(PayTestFixtures.TENANT, "pay-1"))
			.thenReturn(new PayTradeTransactionService.CloseClaim(
					PayTradeTransactionService.CloseDecision.CLAIMED, null));
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-1");
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity)).thenReturn(wxPayService);
		doAnswer(invocation -> wxQuery("SUCCESS")).when(wxPayService).queryOrderV3(any(), anyString());
		when(tradeTransactionService.settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class)))
			.thenReturn(PaySettlementResult.paid("WX-1"));

		PayCloseResult result = service.closeAndCheckPayOrder("ORDER-1");

		assertEquals(PayCloseResult.STATUS_PAID, result.getStatus());
		verify(wxPayService, never()).closeOrderV3(anyString());
	}

	@Test
	void wxQueryClosedMigratesToClosedWithoutClosingAgain() throws Exception {
		PayTradeOrder order = order("CLOSING", "WX_JSAPI_PAY", "0");
		when(tradeOrderMapper.selectOne(any())).thenReturn(order);
		when(tradeTransactionService.claimClose(PayTestFixtures.TENANT, "pay-1"))
			.thenReturn(new PayTradeTransactionService.CloseClaim(
					PayTradeTransactionService.CloseDecision.CLAIMED, null));
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-1");
		when(credentialResolver.resolveForOrder(PayTestFixtures.TENANT, order)).thenReturn(identity);
		WxPayService wxPayService = mock(WxPayService.class);
		when(clientFactory.wxPayService(identity)).thenReturn(wxPayService);
		doAnswer(invocation -> wxQuery("CLOSED")).when(wxPayService).queryOrderV3(any(), anyString());

		PayCloseResult result = service.closeAndCheckPayOrder("ORDER-1");

		assertEquals(PayCloseResult.STATUS_CLOSED, result.getStatus());
		verify(tradeTransactionService).markClosed(PayTestFixtures.TENANT, "pay-1");
		verify(wxPayService, never()).closeOrderV3(anyString());
	}

	@Test
	void orchestratorHasNoLongTransactionAndShortTransactionsAreRequiredNew() throws Exception {
		Method query = PayNotifyRecordServiceImpl.class.getMethod("queryAndSettlePayOrder", String.class,
				String.class);
		Method close = PayNotifyRecordServiceImpl.class.getMethod("closeAndCheckPayOrder", String.class);
		assertNull(query.getAnnotation(Transactional.class), "查单编排层不能有外层事务");
		assertNull(close.getAnnotation(Transactional.class), "关单编排层不能有外层事务");

		for (String methodName : new String[] { "claimClose", "markClosed", "markUnknown", "settleWxQuery",
				"settleAlipayQuery" }) {
			Method method = findMethod(PayTradeTransactionService.class, methodName);
			Transactional transactional = method.getAnnotation(Transactional.class);
			assertNotNull(transactional, methodName + " 必须是短事务");
			assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
		}
		for (String methodName : new String[] { "closeAndCheckPayOrder", "queryAndSettlePayOrder" }) {
			Method method = findMethod(com.aryn.cloud.pay.dubbo.RemotePayServiceImpl.class, methodName);
			assertNull(method.getAnnotation(Transactional.class),
					"RemotePayServiceImpl." + methodName + " 不能有外层事务");
		}
		Method relay = PayEventOutboxService.class.getMethod("relayBatch");
		assertNull(relay.getAnnotation(Transactional.class), "outbox 发送编排层不能有外层事务");
		for (String methodName : new String[] { "resetStaleSending", "claim", "markSent", "markRetry" }) {
			Method method = findMethod(PayEventOutboxTxService.class, methodName);
			Transactional transactional = method.getAnnotation(Transactional.class);
			assertNotNull(transactional, methodName + " 必须是短事务");
			assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
		}
	}

	private Method findMethod(Class<?> type, String name) {
		for (Method method : type.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new IllegalStateException("missing method " + name);
	}

	private PayTradeOrder order(String tradeStatus, String tradeType, String payStatus) {
		return new PayTradeOrder().setId("pay-1")
			.setTenantId(PayTestFixtures.TENANT)
			.setOutTradeNo("ORDER-1")
			.setTradeType(tradeType)
			.setTradeStatus(tradeStatus)
			.setPayStatus(payStatus)
			.setAmount(new BigDecimal("99.90"))
			.setTerminalType("0")
			.setConfigRevisionId("rev-1")
			.setExtra("{}");
	}

	private WxPayOrderQueryV3Result wxQuery(String tradeState) {
		WxPayOrderQueryV3Result result = new WxPayOrderQueryV3Result();
		result.setAppid("wx-synthetic-app");
		result.setMchid("synthetic-mch");
		result.setOutTradeNo("ORDER-1");
		result.setTransactionId("WX-1");
		result.setTradeState(tradeState);
		result.setSuccessTime("2026-10-08T12:00:00+08:00");
		WxPayOrderQueryV3Result.Amount amount = new WxPayOrderQueryV3Result.Amount();
		amount.setTotal(9990);
		amount.setCurrency("CNY");
		result.setAmount(amount);
		return result;
	}

}
