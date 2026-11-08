package com.aryn.cloud.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayEventOutboxService;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.pay.service.PayTradeTransactionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主动查单落账的契约测试。
 *
 * <p>背景（真实缺陷）：C 端充值支付结果页原以「轮询 5 次 × 1.5 秒」判定成败 ——
 * 回调一旦晚于这个窗口，用户已付的钱只能显示成「充值处理中」。根因是判定权放错了
 * 地方：是否支付成功，本来只有支付渠道说了算，轮询次数不该参与判定。
 *
 * <p>这里钉住三条约束，防止回退：
 * <ol>
 *   <li>渠道确认 SUCCESS 时，落账必须走与回调相同的路径（条件更新 + 广播 MQ），
 *       不能另起一套只改本地状态的旁路；</li>
 *   <li>渠道未确认（NOTPAY/USERPAYING 等）或查询失败时，不得误报成功、不得改本地状态；</li>
 *   <li>本地已是已支付时不再打扰渠道，避免重复查询与重复广播。</li>
 * </ol>
 */
class PayOrderQuerySettlementTest {

	private PayEventOutboxService outboxService;

	private PayTradeOrderMapper tradeOrderMapper;

	private IPayConfigService configService;

	private PayCredentialResolver credentialResolver;

	private PayTradeTransactionService tradeTransactionService;

	private TestPayNotifyRecordService service;

	@BeforeEach
	void setUp() {
		outboxService = mock(PayEventOutboxService.class);
		tradeOrderMapper = mock(PayTradeOrderMapper.class);
		configService = mock(IPayConfigService.class);
		credentialResolver = mock(PayCredentialResolver.class);
		tradeTransactionService = mock(PayTradeTransactionService.class);
		when(credentialResolver.resolveForOrder(anyString(), any(PayTradeOrder.class)))
			.thenReturn(PayTestFixtures.wechatIdentity("rev-wx"));
		service = new TestPayNotifyRecordService(outboxService, tradeOrderMapper,
				mock(PayRefundOrderMapper.class), mock(HttpServletRequest.class), configService, credentialResolver,
				mock(PayChannelClientFactory.class), tradeTransactionService, new PayMaterialParser());
		ReflectionTestUtils.setField(service, "baseMapper", mock(PayNotifyRecordMapper.class));
	}

	@Test
	@DisplayName("渠道确认成功 - 走与回调相同的落账与广播路径")
	void channelConfirmedSettlesThroughSamePathAsCallback() {
		// given
		when(tradeOrderMapper.selectOne(any())).thenReturn(pendingWxOrder());
		service.queryResult = queryResult("SUCCESS");
		when(tradeTransactionService.settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class)))
			.thenReturn(PaySettlementResult.paid("WX-1"));

		// when
		PaySettlementResult result = service.queryAndSettlePayOrder("tenant-1", "R20261008001");

		// then - 查单在事务外，落账交给短事务服务
		verify(tradeTransactionService).settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class));
		assertTrue(result.isPaid());
	}

	@Test
	@DisplayName("渠道未确认 - 不落账、不广播、不改本地状态")
	void channelNotConfirmedDoesNotSettle() {
		// given
		when(tradeOrderMapper.selectOne(any())).thenReturn(pendingWxOrder());
		service.queryResult = queryResult("USERPAYING");
		when(tradeTransactionService.settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class)))
			.thenReturn(PaySettlementResult.unpaid("USERPAYING"));

		// when
		PaySettlementResult result = service.queryAndSettlePayOrder("tenant-1", "R20261008001");

		// then
		assertFalse(result.isPaid());
		assertTrue("USERPAYING".equals(result.getTradeState()),
				"应把渠道状态透出，供上层区分「确认中」与「已关闭」");
		verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("渠道查单金额与本地支付单不一致 - 拒绝落账")
	void amountMismatchIsRejected() {
		// given
		when(tradeOrderMapper.selectOne(any())).thenReturn(pendingWxOrder());
		WxPayOrderQueryV3Result mismatch = queryResult("SUCCESS");
		mismatch.getAmount().setTotal(1); // 本地是 99.90 元
		service.queryResult = mismatch;
		when(tradeTransactionService.settleWxQuery(anyString(), any(PayTradeOrder.class),
				any(PayResolvedIdentity.class), any(WxPayOrderQueryV3Result.class)))
			.thenReturn(PaySettlementResult.unpaid("SUCCESS"));

		// when
		PaySettlementResult result = service.queryAndSettlePayOrder("tenant-1", "R20261008001");

		// then - 查单不是校验较弱的旁路
		assertFalse(result.isPaid());
		verify(outboxService, never()).enqueue(anyString(), anyString(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("本地已支付 - 直接返回结果，不再打扰渠道")
	void alreadyPaidSkipsChannelQuery() {
		// given
		PayTradeOrder paid = pendingWxOrder().setPayStatus("1").setChannelOrderNo("WX-1");
		when(tradeOrderMapper.selectOne(any())).thenReturn(paid);

		// when
		PaySettlementResult result = service.queryAndSettlePayOrder("tenant-1", "R20261008001");

		// then
		assertTrue(result.isPaid());
		assertEquals(0, service.queryCount, "本地已支付时不应再访问渠道");
	}

	@Test
	@DisplayName("渠道不可用 - 保持原状态，不误报成功")
	void channelFailureKeepsPending() {
		// given
		when(tradeOrderMapper.selectOne(any())).thenReturn(pendingWxOrder());
		service.queryThrows = true;

		// when
		PaySettlementResult result = service.queryAndSettlePayOrder("tenant-1", "R20261008001");

		// then
		assertFalse(result.isPaid());
		assertNull(result.getChannelOrderNo());
	}

	private PayTradeOrder pendingWxOrder() {
		return new PayTradeOrder().setId("pay-1").setTenantId(PayTestFixtures.TENANT).setOutTradeNo("R20261008001")
			.setTerminalType("0").setTradeType("WX_JSAPI_PAY").setPayStatus("0")
			.setAmount(new BigDecimal("99.90")).setConfigRevisionId("rev-wx");
	}

	private WxPayOrderQueryV3Result queryResult(String tradeState) {
		WxPayOrderQueryV3Result result = new WxPayOrderQueryV3Result();
		result.setAppid("wx-synthetic-app");
		result.setMchid("synthetic-mch");
		result.setOutTradeNo("R20261008001");
		result.setTransactionId("WX-1");
		result.setTradeState(tradeState);
		result.setSuccessTime("2026-10-08T12:00:00+08:00");
		WxPayOrderQueryV3Result.Amount amount = new WxPayOrderQueryV3Result.Amount();
		amount.setTotal(9990);
		amount.setCurrency(PayConstants.CURRENCY);
		result.setAmount(amount);
		return result;
	}

	/** 可覆盖渠道查询的测试子类：不把单测绑死在真实微信 SDK 上 */
	public static final class TestPayNotifyRecordService extends PayNotifyRecordServiceImpl {

		WxPayOrderQueryV3Result queryResult;

		boolean queryThrows;

		int queryCount;

		TestPayNotifyRecordService(PayEventOutboxService outboxService, PayTradeOrderMapper payTradeOrderMapper,
				PayRefundOrderMapper payRefundOrderMapper, HttpServletRequest httpServletRequest,
				IPayConfigService payConfigService, PayCredentialResolver credentialResolver,
				PayChannelClientFactory clientFactory, PayTradeTransactionService tradeTransactionService,
				PayMaterialParser materialParser) {
			super(outboxService, payTradeOrderMapper, payRefundOrderMapper, httpServletRequest, payConfigService,
					credentialResolver, clientFactory, tradeTransactionService, materialParser);
		}

		@Override
		protected WxPayOrderQueryV3Result queryWxOrder(PayResolvedIdentity identity, String outTradeNo)
				throws WxPayException {
			queryCount++;
			if (queryThrows) {
				throw new WxPayException("渠道不可用");
			}
			return queryResult;
		}
	}

}
