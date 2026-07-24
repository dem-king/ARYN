package com.aryn.cloud.pay.service.impl;

import com.alipay.api.AlipayApiException;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayNotifyRecordServiceImplTest {

	private RocketMQTemplate rocketMQTemplate;

	private PayTradeOrderMapper tradeOrderMapper;

	private PayRefundOrderMapper refundOrderMapper;

	private HttpServletRequest request;

	private IPayConfigService configService;

	private TestPayNotifyRecordService service;

	@BeforeEach
	void setUp() {
		rocketMQTemplate = mock(RocketMQTemplate.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		refundOrderMapper = mock(PayRefundOrderMapper.class);
		request = mock(HttpServletRequest.class);
		configService = mock(IPayConfigService.class);
		service = new TestPayNotifyRecordService(rocketMQTemplate, tradeOrderMapper, refundOrderMapper, request,
				configService);
		ReflectionTestUtils.setField(service, "baseMapper", mock(PayNotifyRecordMapper.class));
	}

	@Test
	void alipaySignatureFalseIsRejectedBeforeOrderMutation() {
		service.signatureValid = false;
		when(configService.getConfig(PayConstants.PAY_TYPE_2, "2")).thenReturn(alipayConfig());
		stubRequest(alipayParams());

		assertEquals(PayConstants.ALIPAY_FAIL, service.aliPayNotify("tenant-1", "2", request));

		verify(tradeOrderMapper, never()).selectOne(any());
		verify(tradeOrderMapper, never()).markPaidIfPending(anyString(), anyString(), anyString(), any());
	}

	@Test
	void alipayBusinessFieldsMustMatchConfiguredMerchantAndStoredAmount() {
		PayTradeOrder order = order();
		Map<String, String> params = alipayParams();
		assertTrue(service.validAlipayNotification(params, order, alipayConfig()));

		params.put("app_id", "attacker-app");
		assertFalse(service.validAlipayNotification(params, order, alipayConfig()));
		params.put("app_id", "app-1");
		params.put("total_amount", "0.01");
		assertFalse(service.validAlipayNotification(params, order, alipayConfig()));
		params.put("total_amount", "99.90");
		PayConfigVO missingMerchant = alipayConfig();
		missingMerchant.setMchId(null);
		assertFalse(service.validAlipayNotification(params, order, missingMerchant));
	}

	@Test
	void wechatBusinessFieldsMustMatchConfiguredMerchantAndStoredAmount() {
		WxPayNotifyV3Result.DecryptNotifyResult result = new WxPayNotifyV3Result.DecryptNotifyResult();
		result.setAppid("app-1");
		result.setMchid("merchant-1");
		result.setTradeState("SUCCESS");
		result.setTransactionId("WX-1");
		result.setSuccessTime("2026-07-23T12:00:00+08:00");
		WxPayNotifyV3Result.Amount amount = new WxPayNotifyV3Result.Amount();
		amount.setTotal(9990);
		amount.setCurrency(PayConstants.CURRENCY);
		result.setAmount(amount);

		assertTrue(service.validWxPayNotification(result, order(), wechatConfig()));
		amount.setTotal(1);
		assertFalse(service.validWxPayNotification(result, order(), wechatConfig()));
	}

	@Test
	void sameChannelDuplicateIsAcknowledgedWithoutSendingAnotherMessage() {
		service.signatureValid = true;
		when(configService.getConfig(PayConstants.PAY_TYPE_2, "2")).thenReturn(alipayConfig());
		stubRequest(alipayParams());
		PayTradeOrder pending = order();
		when(tradeOrderMapper.selectOne(any())).thenReturn(pending);
		when(tradeOrderMapper.markPaidIfPending("tenant-1", "pay-1", "ALI-1", pending.getPaySuccessTime()))
			.thenReturn(0);
		PayTradeOrder paid = order().setPayStatus("1").setChannelOrderNo("ALI-1");
		when(tradeOrderMapper.selectById("pay-1")).thenReturn(paid);

		assertEquals(PayConstants.ALIPAY_SUCCESS, service.aliPayNotify("tenant-1", "2", request));

		verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong());
	}

	@Test
	void successfulAlipayNotificationRequiresSuccessfulMessageDelivery() {
		service.signatureValid = true;
		when(configService.getConfig(PayConstants.PAY_TYPE_2, "2")).thenReturn(alipayConfig());
		stubRequest(alipayParams());
		when(tradeOrderMapper.selectOne(any())).thenReturn(order());
		when(tradeOrderMapper.markPaidIfPending("tenant-1", "pay-1", "ALI-1",
				java.time.LocalDateTime.of(2026, 7, 23, 12, 0))).thenReturn(1);
		SendResult sendResult = new SendResult();
		sendResult.setSendStatus(SendStatus.SEND_OK);
		when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong())).thenReturn(sendResult);

		assertEquals(PayConstants.ALIPAY_SUCCESS, service.aliPayNotify("tenant-1", "2", request));
		verify(rocketMQTemplate).syncSend(anyString(), any(Message.class), anyLong());
	}

	private void stubRequest(Map<String, String> params) {
		Map<String, String[]> servletParams = new LinkedHashMap<>();
		params.forEach((key, value) -> servletParams.put(key, new String[] { value }));
		when(request.getParameterMap()).thenReturn(servletParams);
		params.forEach((key, value) -> when(request.getParameter(key)).thenReturn(value));
	}

	private Map<String, String> alipayParams() {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("app_id", "app-1");
		params.put("seller_id", "merchant-1");
		params.put("out_trade_no", "ORDER-1");
		params.put("trade_no", "ALI-1");
		params.put("trade_status", "TRADE_SUCCESS");
		params.put("total_amount", "99.90");
		params.put("gmt_payment", "2026-07-23 12:00:00");
		params.put("sign", "signature");
		return params;
	}

	private PayTradeOrder order() {
		return new PayTradeOrder().setId("pay-1").setTenantId("tenant-1").setOutTradeNo("ORDER-1")
			.setTerminalType("2").setTradeType("ALI_H5_PAY").setPayStatus("0")
			.setAmount(new BigDecimal("99.90")).setExtra("{\"payType\":\"2\"}")
			.setPaySuccessTime(java.time.LocalDateTime.of(2026, 7, 23, 12, 0));
	}

	private PayConfigVO alipayConfig() {
		PayConfigVO config = new PayConfigVO();
		config.setAppId("app-1");
		config.setMchId("merchant-1");
		config.setPrivateKeyPath("/cert/alipay.crt");
		return config;
	}

	private PayConfigVO wechatConfig() {
		PayConfigVO config = new PayConfigVO();
		config.setAppId("app-1");
		config.setMchId("merchant-1");
		return config;
	}

	private static final class TestPayNotifyRecordService extends PayNotifyRecordServiceImpl {

		private boolean signatureValid;

		private TestPayNotifyRecordService(RocketMQTemplate rocketMQTemplate,
				PayTradeOrderMapper payTradeOrderMapper, PayRefundOrderMapper payRefundOrderMapper,
				HttpServletRequest httpServletRequest, IPayConfigService payConfigService) {
			super(rocketMQTemplate, payTradeOrderMapper, payRefundOrderMapper, httpServletRequest, payConfigService);
		}

		@Override
		protected boolean verifyAlipaySignature(Map<String, String> params, String alipayPublicCertPath)
				throws AlipayApiException {
			return signatureValid;
		}
	}

}
