package com.aryn.cloud.pay.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayNotifyRecord;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayTradeStatusEnum;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 支付域短事务边界：关单意图、终态推进、查单落账+outbox。
 *
 * <p>本类只做数据库操作，禁止调用任何渠道网络接口；渠道查询/关单由编排层
 * 在事务外完成，再回到这里用 REQUIRES_NEW 短事务收敛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayTradeTransactionService {

	private static final long CLOSE_CLAIM_STALE_MINUTES = 2;

	private final PayTradeOrderMapper tradeOrderMapper;

	private final PayNotifyRecordMapper notifyRecordMapper;

	private final PayEventOutboxService outboxService;

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public CloseClaim claimClose(String tenantId, String orderId) {
		PayTradeOrder current = tradeOrderMapper.selectById(orderId);
		if (current == null) {
			return new CloseClaim(CloseDecision.UNKNOWN, "支付单不存在");
		}
		if (CommonConstants.YES.equals(current.getPayStatus())) {
			return new CloseClaim(CloseDecision.PAID, "订单已支付");
		}
		PayTradeStatusEnum status = PayTradeStatusEnum.fromCode(current.getTradeStatus());
		if (status == PayTradeStatusEnum.PAID) {
			return new CloseClaim(CloseDecision.PAID, "订单已支付");
		}
		if (status == PayTradeStatusEnum.CLOSED) {
			return new CloseClaim(CloseDecision.CLOSED, "支付单已关闭");
		}
		if (status == PayTradeStatusEnum.SUBMITTING) {
			return new CloseClaim(CloseDecision.UNKNOWN, "支付正在提交，请稍后重试");
		}
		int updated = tradeOrderMapper.claimClosing(tenantId, orderId,
				LocalDateTime.now().minusMinutes(CLOSE_CLAIM_STALE_MINUTES));
		if (updated == 1) {
			return new CloseClaim(CloseDecision.CLAIMED, null);
		}
		PayTradeOrder latest = tradeOrderMapper.selectById(orderId);
		if (latest != null && CommonConstants.YES.equals(latest.getPayStatus())) {
			return new CloseClaim(CloseDecision.PAID, "订单已支付");
		}
		if (latest != null && PayTradeStatusEnum.CLOSED.name().equals(latest.getTradeStatus())) {
			return new CloseClaim(CloseDecision.CLOSED, "支付单已关闭");
		}
		return new CloseClaim(CloseDecision.UNKNOWN, "关单正在处理中，请稍后重试");
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markClosed(String tenantId, String orderId) {
		tradeOrderMapper.markClosed(tenantId, orderId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void markUnknown(String tenantId, String orderId, String errCode, String errMsg) {
		tradeOrderMapper.markUnknown(tenantId, orderId, errCode,
				errMsg == null || errMsg.length() <= 240 ? errMsg : errMsg.substring(0, 240));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public PaySettlementResult settleWxQuery(String tenantId, PayTradeOrder order, PayResolvedIdentity identity,
			WxPayOrderQueryV3Result queryResult) {
		String tradeState = queryResult == null ? null : queryResult.getTradeState();
		if (queryResult == null || !"SUCCESS".equals(tradeState)) {
			return PaySettlementResult.unpaid(tradeState);
		}
		if (!Objects.equals(identity.getAppId(), queryResult.getAppid())
				|| !Objects.equals(identity.getMchId(), queryResult.getMchid())
				|| !Objects.equals(order.getOutTradeNo(), queryResult.getOutTradeNo())
				|| queryResult.getAmount() == null || !PayConstants.CURRENCY.equals(queryResult.getAmount().getCurrency())
				|| !matchesCents(order.getAmount(), queryResult.getAmount().getTotal())) {
			log.warn("微信查单业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, order.getOutTradeNo());
			return PaySettlementResult.unpaid(tradeState);
		}
		LocalDateTime paySuccessTime = parseOffsetDateTime(queryResult.getSuccessTime());
		if (paySuccessTime == null || !StringUtils.hasText(queryResult.getTransactionId())) {
			log.warn("微信查单缺少支付时间或渠道单号, tenantId: {}, orderNo: {}", tenantId, order.getOutTradeNo());
			return PaySettlementResult.unpaid(tradeState);
		}
		int updated = tradeOrderMapper.markPaidIfPending(tenantId, order.getId(), queryResult.getTransactionId(),
				paySuccessTime);
		if (updated == 0) {
			PayTradeOrder current = tradeOrderMapper.selectById(order.getId());
			if (current != null && CommonConstants.YES.equals(current.getPayStatus())) {
				return PaySettlementResult.paid(current.getChannelOrderNo());
			}
			return PaySettlementResult.unpaid(tradeState);
		}
		order.setChannelOrderNo(queryResult.getTransactionId());
		order.setPayStatus(CommonConstants.YES);
		order.setTradeStatus(PayTradeStatusEnum.PAID.name());
		order.setPaySuccessTime(paySuccessTime);
		saveQueryNotifyAndEvent(tenantId, order, JSON.toJSONString(queryResult),
				queryResult.getTransactionId());
		return PaySettlementResult.paid(queryResult.getTransactionId());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public PaySettlementResult settleAlipayQuery(String tenantId, PayTradeOrder order,
			AlipayTradeQueryResponse queryResult) {
		if (queryResult == null || !queryResult.isSuccess()) {
			return PaySettlementResult.unpaid(queryResult == null ? null : queryResult.getSubCode());
		}
		String tradeStatus = queryResult.getTradeStatus();
		if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
			return PaySettlementResult.unpaid(tradeStatus);
		}
		if (!Objects.equals(order.getOutTradeNo(), queryResult.getOutTradeNo())
				|| !matchesAmount(order.getAmount(), queryResult.getTotalAmount())
				|| !StringUtils.hasText(queryResult.getTradeNo())
				|| queryResult.getSendPayDate() == null) {
			log.warn("支付宝查单业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, order.getOutTradeNo());
			return PaySettlementResult.unpaid(tradeStatus);
		}
		LocalDateTime paySuccessTime = queryResult.getSendPayDate().toInstant()
			.atZone(ZoneId.systemDefault())
			.toLocalDateTime();
		int updated = tradeOrderMapper.markPaidIfPending(tenantId, order.getId(), queryResult.getTradeNo(),
				paySuccessTime);
		if (updated == 0) {
			PayTradeOrder current = tradeOrderMapper.selectById(order.getId());
			if (current != null && CommonConstants.YES.equals(current.getPayStatus())) {
				return PaySettlementResult.paid(current.getChannelOrderNo());
			}
			return PaySettlementResult.unpaid(tradeStatus);
		}
		order.setChannelOrderNo(queryResult.getTradeNo());
		order.setPayStatus(CommonConstants.YES);
		order.setTradeStatus(PayTradeStatusEnum.PAID.name());
		order.setPaySuccessTime(paySuccessTime);
		saveQueryNotifyAndEvent(tenantId, order, JSON.toJSONString(queryResult), queryResult.getTradeNo());
		return PaySettlementResult.paid(queryResult.getTradeNo());
	}

	private void saveQueryNotifyAndEvent(String tenantId, PayTradeOrder order, String request, String channelOrderNo) {
		PayNotifyRecord record = new PayNotifyRecord();
		record.setTenantId(tenantId);
		record.setOutTradeNo(order.getOutTradeNo());
		record.setChannelOrderNo(channelOrderNo);
		record.setRequest(request);
		record.setResponse("query-settled");
		record.setType(PayConstants.PAY_NOTIFY_TYPE);
		notifyRecordMapper.insert(record);

		JSONObject message = new JSONObject();
		message.put(PayConstants.OUT_TRADE_NO, order.getOutTradeNo());
		message.put(PayConstants.CHANNEL_ORDER_NO, channelOrderNo);
		message.put(PayConstants.PAY_SUCCESS_TIME, order.getPaySuccessTime());
		message.put(PayConstants.EXTRA_PARAMS, order.getExtra());
		message.put(PayConstants.TENANT_ID, tenantId);
		outboxService.enqueue(tenantId, "PAY_SUCCESS", order.getOutTradeNo(), RocketMqConstants.PAY_NOTIFY_TOPIC,
				message.toJSONString());
	}

	private boolean matchesAmount(BigDecimal expected, String actual) {
		if (expected == null || actual == null || actual.isBlank()) {
			return false;
		}
		try {
			return expected.compareTo(new BigDecimal(actual)) == 0;
		}
		catch (NumberFormatException exception) {
			return false;
		}
	}

	private boolean matchesCents(BigDecimal expected, Integer actual) {
		if (expected == null || actual == null) {
			return false;
		}
		try {
			return expected.movePointRight(2).intValueExact() == actual;
		}
		catch (ArithmeticException exception) {
			return false;
		}
	}

	private LocalDateTime parseOffsetDateTime(String value) {
		try {
			return OffsetDateTime.parse(value).toLocalDateTime();
		}
		catch (RuntimeException exception) {
			return null;
		}
	}

	public enum CloseDecision {
		CLAIMED, PAID, CLOSED, UNKNOWN
	}

	public record CloseClaim(CloseDecision decision, String message) {
	}

}
