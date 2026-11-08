package com.aryn.cloud.pay.service;

import com.alipay.api.response.AlipayTradeQueryResponse;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayTradeTransactionServiceTest {

	private PayTradeOrderMapper tradeOrderMapper;

	private PayNotifyRecordMapper notifyRecordMapper;

	private PayEventOutboxService outboxService;

	private PayTradeTransactionService service;

	@BeforeEach
	void setUp() {
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		notifyRecordMapper = mock(PayNotifyRecordMapper.class);
		outboxService = mock(PayEventOutboxService.class);
		service = new PayTradeTransactionService(tradeOrderMapper, notifyRecordMapper, outboxService);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void claimCloseMovesPendingOrderToClosing() {
		PayTradeOrder order = order("PENDING");
		when(tradeOrderMapper.selectById("pay-1")).thenReturn(order);
		when(tradeOrderMapper.claimClosing(eq(PayTestFixtures.TENANT), eq("pay-1"), any(LocalDateTime.class)))
			.thenReturn(1);

		PayTradeTransactionService.CloseClaim claim = service.claimClose(PayTestFixtures.TENANT, "pay-1");

		assertEquals(PayTradeTransactionService.CloseDecision.CLAIMED, claim.decision());
	}

	@Test
	void claimCloseRefusesPaidOrClosedOrSubmittingOrder() {
		PayTradeOrder paid = order("PAID").setPayStatus("1");
		when(tradeOrderMapper.selectById("paid")).thenReturn(paid);
		assertEquals(PayTradeTransactionService.CloseDecision.PAID,
				service.claimClose(PayTestFixtures.TENANT, "paid").decision());

		when(tradeOrderMapper.selectById("closed")).thenReturn(order("CLOSED"));
		assertEquals(PayTradeTransactionService.CloseDecision.CLOSED,
				service.claimClose(PayTestFixtures.TENANT, "closed").decision());

		when(tradeOrderMapper.selectById("submitting")).thenReturn(order("SUBMITTING"));
		assertEquals(PayTradeTransactionService.CloseDecision.UNKNOWN,
				service.claimClose(PayTestFixtures.TENANT, "submitting").decision());
	}

	@Test
	void wxQuerySettlesWithShortTransactionWrites() {
		PayTradeOrder order = order("CLOSING");
		when(tradeOrderMapper.markPaidIfPending(eq(PayTestFixtures.TENANT), eq("pay-1"), eq("WX-1"),
				any(LocalDateTime.class))).thenReturn(1);

		PaySettlementResult result = service.settleWxQuery(PayTestFixtures.TENANT, order,
				PayTestFixtures.wechatIdentity("rev-1"), wxQuery(9990));

		assertTrue(result.isPaid());
		verify(notifyRecordMapper).insert(any(com.aryn.cloud.pay.api.entity.PayNotifyRecord.class));
		verify(outboxService).enqueue(eq(PayTestFixtures.TENANT), eq("PAY_SUCCESS"), eq("ORDER-1"),
				anyString(), anyString());
	}

	@Test
	void wxQueryAmountMismatchNeverWrites() {
		PayTradeOrder order = order("CLOSING");

		PaySettlementResult result = service.settleWxQuery(PayTestFixtures.TENANT, order,
				PayTestFixtures.wechatIdentity("rev-1"), wxQuery(1));

		assertFalse(result.isPaid());
		verify(tradeOrderMapper, never()).markPaidIfPending(anyString(), anyString(), anyString(), any());
		verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	void alipayQuerySettlesWithShortTransactionWrites() {
		PayTradeOrder order = order("CLOSING");
		when(tradeOrderMapper.markPaidIfPending(eq(PayTestFixtures.TENANT), eq("pay-1"), eq("ALI-1"),
				any(LocalDateTime.class))).thenReturn(1);
		AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
		response.setCode("10000");
		response.setTradeStatus("TRADE_SUCCESS");
		response.setOutTradeNo("ORDER-1");
		response.setTotalAmount("99.90");
		response.setTradeNo("ALI-1");
		response.setSendPayDate(new Date());

		PaySettlementResult result = service.settleAlipayQuery(PayTestFixtures.TENANT, order, response);

		assertTrue(result.isPaid());
		verify(notifyRecordMapper).insert(any(com.aryn.cloud.pay.api.entity.PayNotifyRecord.class));
		verify(outboxService).enqueue(eq(PayTestFixtures.TENANT), eq("PAY_SUCCESS"), eq("ORDER-1"),
				anyString(), anyString());
	}

	@Test
	void alipayAmountMismatchNeverWrites() {
		PayTradeOrder order = order("CLOSING");
		AlipayTradeQueryResponse response = new AlipayTradeQueryResponse();
		response.setCode("10000");
		response.setTradeStatus("TRADE_SUCCESS");
		response.setOutTradeNo("ORDER-1");
		response.setTotalAmount("0.01");
		response.setTradeNo("ALI-1");
		response.setSendPayDate(new Date());

		assertFalse(service.settleAlipayQuery(PayTestFixtures.TENANT, order, response).isPaid());
		verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	private PayTradeOrder order(String tradeStatus) {
		return new PayTradeOrder().setId("pay-1")
			.setTenantId(PayTestFixtures.TENANT)
			.setOutTradeNo("ORDER-1")
			.setAmount(new BigDecimal("99.90"))
			.setPayStatus("0")
			.setTradeStatus(tradeStatus)
			.setExtra("{}");
	}

	private WxPayOrderQueryV3Result wxQuery(int total) {
		WxPayOrderQueryV3Result result = new WxPayOrderQueryV3Result();
		result.setAppid("wx-synthetic-app");
		result.setMchid("synthetic-mch");
		result.setOutTradeNo("ORDER-1");
		result.setTransactionId("WX-1");
		result.setTradeState("SUCCESS");
		result.setSuccessTime("2026-10-08T12:00:00+08:00");
		WxPayOrderQueryV3Result.Amount amount = new WxPayOrderQueryV3Result.Amount();
		amount.setTotal(total);
		amount.setCurrency("CNY");
		result.setAmount(amount);
		return result;
	}

}
