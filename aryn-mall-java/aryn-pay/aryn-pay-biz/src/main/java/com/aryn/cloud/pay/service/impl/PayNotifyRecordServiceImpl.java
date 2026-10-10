
package com.aryn.cloud.pay.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Response;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayNotifyRecord;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.config.WxPayConfiguration;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.IPayNotifyRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * 支付订单
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayNotifyRecordServiceImpl extends ServiceImpl<PayNotifyRecordMapper, PayNotifyRecord>
		implements IPayNotifyRecordService {

	private final RocketMQTemplate rocketMQTemplate;

	private final PayTradeOrderMapper payTradeOrderMapper;

	private final PayRefundOrderMapper payRefundOrderMapper;

	private final HttpServletRequest httpServletRequest;

	private final IPayConfigService payConfigService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String wxPayNotify(String tenantId, String terminalType, String notifyData) {
		WxPayNotifyV3Result wxPayOrderNotifyV3Result;
		try {
			wxPayOrderNotifyV3Result = parseWxPayNotify(terminalType, notifyData);
		}
		catch (WxPayException e) {
			log.warn("微信支付回调验签或解密失败, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return WxPayNotifyV3Response.fail("解密失败");
		}
		WxPayNotifyV3Result.DecryptNotifyResult decryptNotifyResult = wxPayOrderNotifyV3Result.getResult();
		if (decryptNotifyResult == null || !StringUtils.hasText(decryptNotifyResult.getOutTradeNo())) {
			return WxPayNotifyV3Response.fail("回调订单号为空");
		}
		String outTradeNo = decryptNotifyResult.getOutTradeNo();
		PayTradeOrder orderInfo = payTradeOrderMapper
			.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
				.eq(PayTradeOrder::getOutTradeNo, outTradeNo)
				.eq(PayTradeOrder::getTerminalType, terminalType)
				.likeRight(PayTradeOrder::getTradeType, "WX_")
				.last("LIMIT 1"));
		if (ObjectUtil.isNull(orderInfo)) {
			return WxPayNotifyV3Response.fail("order not found! orderNo: " + outTradeNo);
		}
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_1, terminalType);
		if (!validWxPayNotification(decryptNotifyResult, orderInfo, payConfig)) {
			log.warn("微信支付回调业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return WxPayNotifyV3Response.fail("回调数据校验失败");
		}
		LocalDateTime paySuccessTime = parseOffsetDateTime(decryptNotifyResult.getSuccessTime());
		if (paySuccessTime == null) {
			return WxPayNotifyV3Response.fail("支付时间格式错误");
		}
		int updated = payTradeOrderMapper.markPaidIfPending(tenantId, orderInfo.getId(),
				decryptNotifyResult.getTransactionId(), paySuccessTime);
		if (updated == 0) {
			return duplicateWxPayResponse(orderInfo.getId(), decryptNotifyResult.getTransactionId());
		}
		orderInfo.setChannelOrderNo(decryptNotifyResult.getTransactionId());
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setPaySuccessTime(paySuccessTime);
		saveNotifyRecord(tenantId, outTradeNo, decryptNotifyResult.getTransactionId(),
				JSON.toJSONString(decryptNotifyResult), WxPayNotifyV3Response.success("成功"), PayConstants.PAY_NOTIFY_TYPE);
		sendPaySuccess(orderInfo);
		return WxPayNotifyV3Response.success("成功");
	}

	/**
	 * 主动向渠道查单并落账：与回调共用同一段落账+广播逻辑，只是结论来源从
	 * 「渠道推来的回调」变成「我们主动去问渠道」。
	 *
	 * <p>回调未达而用户已付款时，业务侧的待支付单据必须能靠这一路径收敛，
	 * 否则用户会长时间停在「处理中」。因此这里不新造落账代码 —— 任何偏移
	 * 都会让「查单确认」与「回调确认」产生两种后果。
	 *
	 * <p>租户上下文由调用层负责（本方法经 Dubbo 暴露，过滤器会透传租户），
	 * 与 {@link #wxPayNotify} 等回调方法保持同一约定；tenantId 仅用于
	 * 条件更新时的租户隔离。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public PaySettlementResult queryAndSettlePayOrder(String tenantId, String outTradeNo) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(outTradeNo)) {
			return PaySettlementResult.unpaid();
		}
		PayTradeOrder orderInfo = payTradeOrderMapper.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, outTradeNo)
			.last("LIMIT 1"));
		if (orderInfo == null) {
			return PaySettlementResult.unpaid();
		}
		// 已被回调（或上一次查单）落账：直接返回成功，不再打扰渠道
		if (CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			return PaySettlementResult.paid(orderInfo.getChannelOrderNo());
		}
		// 只对微信交易查单；其余渠道没有可用的主动查询实现
		if (!StringUtils.hasText(orderInfo.getTradeType())
				|| !orderInfo.getTradeType().startsWith("WX_")) {
			return PaySettlementResult.unpaid();
		}
		return queryAndSettleWxPayOrder(tenantId, orderInfo);
	}

	/**
	 * 向微信查询单笔交易并按结果落账。
	 *
	 * <p>取到 SUCCESS 才落账，且金额、商户号与本地支付单逐项核对后再落账 ——
	 * 与回调校验同一套口径，避免「查单」成为校验较弱的旁路。
	 */
	private PaySettlementResult queryAndSettleWxPayOrder(String tenantId, PayTradeOrder orderInfo) {
		WxPayOrderQueryV3Result queryResult;
		try {
			queryResult = queryWxOrder(orderInfo.getTerminalType(), orderInfo.getOutTradeNo());
		}
		catch (Exception e) {
			// 渠道不可用/订单在渠道不存在：保持原状态，交由业务侧继续等待或人工处理
			log.warn("微信查单失败, tenantId: {}, orderNo: {}, msg: {}", tenantId, orderInfo.getOutTradeNo(),
					e.getMessage());
			return PaySettlementResult.unpaid();
		}
		String tradeState = queryResult == null ? null : queryResult.getTradeState();
		if (queryResult == null || !"SUCCESS".equals(tradeState)) {
			return PaySettlementResult.unpaid(tradeState);
		}
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_1, orderInfo.getTerminalType());
		if (payConfig == null
				|| !Objects.equals(payConfig.getAppId(), queryResult.getAppid())
				|| !Objects.equals(payConfig.getMchId(), queryResult.getMchid())
				|| queryResult.getAmount() == null
				|| !PayConstants.CURRENCY.equals(queryResult.getAmount().getCurrency())
				|| !matchesCents(orderInfo.getAmount(), queryResult.getAmount().getTotal())) {
			log.warn("微信查单业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, orderInfo.getOutTradeNo());
			return PaySettlementResult.unpaid(tradeState);
		}
		LocalDateTime paySuccessTime = parseOffsetDateTime(queryResult.getSuccessTime());
		if (paySuccessTime == null || !StringUtils.hasText(queryResult.getTransactionId())) {
			log.warn("微信查单缺少支付时间或渠道单号, tenantId: {}, orderNo: {}", tenantId, orderInfo.getOutTradeNo());
			return PaySettlementResult.unpaid(tradeState);
		}
		// 与回调一致：条件更新抢占，只有真正推进状态的那一次才广播
		int updated = payTradeOrderMapper.markPaidIfPending(tenantId, orderInfo.getId(),
				queryResult.getTransactionId(), paySuccessTime);
		if (updated == 0) {
			PayTradeOrder current = payTradeOrderMapper.selectById(orderInfo.getId());
			if (current != null && CommonConstants.YES.equals(current.getPayStatus())) {
				return PaySettlementResult.paid(current.getChannelOrderNo());
			}
			return PaySettlementResult.unpaid(tradeState);
		}
		orderInfo.setChannelOrderNo(queryResult.getTransactionId());
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setPaySuccessTime(paySuccessTime);
		saveNotifyRecord(tenantId, orderInfo.getOutTradeNo(), queryResult.getTransactionId(),
				JSON.toJSONString(queryResult), "query-settled", PayConstants.PAY_NOTIFY_TYPE);
		sendPaySuccess(orderInfo);
		return PaySettlementResult.paid(queryResult.getTransactionId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String aliPayNotify(String tenantId, String terminalType, HttpServletRequest request) {
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_2, terminalType);
		if (payConfig == null) {
			return PayConstants.ALIPAY_FAIL;
		}

		Map<String, String> params = new HashMap<>();
		Map<String, String[]> requestParams = request.getParameterMap();
		for (Iterator<String> iter = requestParams.keySet().iterator(); iter.hasNext();) {
			String name = iter.next();
			String[] values = requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
			}
			params.put(name, valueStr);
		}
		try {
			if (!verifyAlipaySignature(params, payConfig.getPrivateKeyPath())) {
				log.warn("支付宝回调验签失败, tenantId: {}, terminalType: {}", tenantId, terminalType);
				return PayConstants.ALIPAY_FAIL;
			}
		}
		catch (AlipayApiException e) {
			log.warn("支付宝回调验签异常, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return PayConstants.ALIPAY_FAIL;
		}
		String outTradeNo = request.getParameter("out_trade_no");
		String tradeStatus = request.getParameter("trade_status");
		if (!StringUtils.hasText(outTradeNo)) {
			return PayConstants.ALIPAY_FAIL;
		}
		if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
			return PayConstants.ALIPAY_SUCCESS;
		}
		PayTradeOrder orderInfo = payTradeOrderMapper.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, outTradeNo)
			.eq(PayTradeOrder::getTerminalType, terminalType)
			.likeRight(PayTradeOrder::getTradeType, "ALI_")
			.last("LIMIT 1"));
		if (orderInfo == null || !validAlipayNotification(params, orderInfo, payConfig)) {
			log.warn("支付宝回调业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return PayConstants.ALIPAY_FAIL;
		}
		String channelOrderNo = request.getParameter("trade_no");
		LocalDateTime paySuccessTime;
		try {
			paySuccessTime = LocalDateTime.parse(request.getParameter("gmt_payment"),
					DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		}
		catch (RuntimeException exception) {
			return PayConstants.ALIPAY_FAIL;
		}
		int updated = payTradeOrderMapper.markPaidIfPending(tenantId, orderInfo.getId(), channelOrderNo,
				paySuccessTime);
		if (updated == 0) {
			return duplicateAlipayResponse(orderInfo.getId(), channelOrderNo);
		}
		orderInfo.setChannelOrderNo(channelOrderNo);
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setPaySuccessTime(paySuccessTime);
		saveNotifyRecord(tenantId, outTradeNo, channelOrderNo, JSON.toJSONString(params),
				PayConstants.ALIPAY_SUCCESS, PayConstants.PAY_NOTIFY_TYPE);
		sendPaySuccess(orderInfo);
		return PayConstants.ALIPAY_SUCCESS;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String wxPayRefundNotify(String tenantId, String terminalType, String params) {
		WxPayRefundNotifyV3Result wxPayRefundNotifyV3Result;
		try {
			wxPayRefundNotifyV3Result = parseWxRefundNotify(terminalType, params);
		}
		catch (WxPayException e) {
			log.warn("微信退款回调验签或解密失败, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return WxPayNotifyV3Response.fail("解密失败");
		}
		WxPayRefundNotifyV3Result.DecryptNotifyResult result = wxPayRefundNotifyV3Result.getResult();
		if (result == null || !StringUtils.hasText(result.getOutRefundNo())) {
			return WxPayNotifyV3Response.fail("回调退款单号为空");
		}
		PayRefundOrder payRefundOrder = payRefundOrderMapper.selectOne(
				Wrappers.<PayRefundOrder>lambdaQuery().eq(PayRefundOrder::getRefundTradeNo, result.getOutRefundNo()));
		if (ObjectUtil.isNull(payRefundOrder)) {
			return WxPayNotifyV3Response.fail("order not found! orderNo: " + result.getOutRefundNo());
		}
		PayConfigVO payConfig = payConfigService.getConfig(PayConstants.PAY_TYPE_1, terminalType);
		if (!validWxRefundNotification(result, payRefundOrder, payConfig)) {
			log.warn("微信退款回调业务字段校验失败, tenantId: {}, refundNo: {}", tenantId,
					result.getOutRefundNo());
			return WxPayNotifyV3Response.fail("回调数据校验失败");
		}
		LocalDateTime refundSuccessTime = parseOffsetDateTime(result.getSuccessTime());
		if (refundSuccessTime == null) {
			return WxPayNotifyV3Response.fail("退款时间格式错误");
		}
		int updated = payRefundOrderMapper.markRefundedIfProcessing(tenantId, payRefundOrder.getId(),
				result.getRefundId(), refundSuccessTime);
		if (updated == 0) {
			return duplicateWxRefundResponse(payRefundOrder.getId(), result.getRefundId());
		}
		payRefundOrder.setChannelRefundNo(result.getRefundId());
		payRefundOrder.setRefundStatus(PayRefundOrderStatusEnum.STATUS_2.getCode());
		payRefundOrder.setRefundSuccessTime(refundSuccessTime);
		saveNotifyRecord(tenantId, result.getOutRefundNo(), result.getRefundId(), JSON.toJSONString(result),
				WxPayNotifyV3Response.success("成功"), PayConstants.REFUND_NOTIFY_TYPE);
		sendRefundSuccess(payRefundOrder);
		return WxPayNotifyV3Response.success("成功");
	}

	/**
	 * 向微信查询单笔交易（可覆写以便单元测试与后续替换实现）。
	 */
	protected WxPayOrderQueryV3Result queryWxOrder(String terminalType, String outTradeNo) throws WxPayException {
		return WxPayConfiguration.wxPayService(terminalType).queryOrderV3(null, outTradeNo);
	}

	protected WxPayNotifyV3Result parseWxPayNotify(String terminalType, String notifyData) throws WxPayException {
		return WxPayConfiguration.wxPayService(terminalType)
			.parseOrderNotifyV3Result(notifyData, buildSignatureHeader());
	}

	protected WxPayRefundNotifyV3Result parseWxRefundNotify(String terminalType, String notifyData)
			throws WxPayException {
		return WxPayConfiguration.wxPayService(terminalType)
			.parseRefundNotifyV3Result(notifyData, buildSignatureHeader());
	}

	protected boolean verifyAlipaySignature(Map<String, String> params, String alipayPublicCertPath)
			throws AlipayApiException {
		return AlipaySignature.certVerifyV1(params, alipayPublicCertPath, "UTF-8", "RSA2");
	}

	boolean validWxPayNotification(WxPayNotifyV3Result.DecryptNotifyResult result, PayTradeOrder order,
			PayConfigVO config) {
		return config != null && result.getAmount() != null
				&& "SUCCESS".equals(result.getTradeState())
				&& Objects.equals(config.getAppId(), result.getAppid())
				&& Objects.equals(config.getMchId(), result.getMchid())
				&& PayConstants.CURRENCY.equals(result.getAmount().getCurrency())
				&& matchesCents(order.getAmount(), result.getAmount().getTotal())
				&& StringUtils.hasText(result.getTransactionId())
				&& StringUtils.hasText(result.getSuccessTime());
	}

	boolean validAlipayNotification(Map<String, String> params, PayTradeOrder order, PayConfigVO config) {
		if (!Objects.equals(config.getAppId(), params.get("app_id"))
				|| !matchesAmount(order.getAmount(), params.get("total_amount"))
				|| !StringUtils.hasText(params.get("trade_no"))
				|| !StringUtils.hasText(params.get("gmt_payment"))) {
			return false;
		}
		return StringUtils.hasText(config.getMchId()) && Objects.equals(config.getMchId(), params.get("seller_id"));
	}

	boolean validWxRefundNotification(WxPayRefundNotifyV3Result.DecryptNotifyResult result,
			PayRefundOrder refundOrder, PayConfigVO config) {
		return config != null && result.getAmount() != null
				&& "SUCCESS".equals(result.getRefundStatus())
				&& Objects.equals(config.getMchId(), result.getMchid())
				&& Objects.equals(refundOrder.getOutTradeNo(), result.getOutTradeNo())
				&& matchesCents(refundOrder.getPayAmount(), result.getAmount().getTotal())
				&& matchesCents(refundOrder.getRefundAmount(), result.getAmount().getRefund())
				&& StringUtils.hasText(result.getRefundId())
				&& StringUtils.hasText(result.getSuccessTime());
	}

	private boolean matchesAmount(BigDecimal expected, String actual) {
		if (expected == null || !StringUtils.hasText(actual)) {
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

	private String duplicateWxPayResponse(String orderId, String channelOrderNo) {
		PayTradeOrder current = payTradeOrderMapper.selectById(orderId);
		if (current != null && CommonConstants.YES.equals(current.getPayStatus())
				&& Objects.equals(current.getChannelOrderNo(), channelOrderNo)) {
			return WxPayNotifyV3Response.success("成功");
		}
		return WxPayNotifyV3Response.fail("支付状态冲突");
	}

	private String duplicateAlipayResponse(String orderId, String channelOrderNo) {
		PayTradeOrder current = payTradeOrderMapper.selectById(orderId);
		if (current != null && CommonConstants.YES.equals(current.getPayStatus())
				&& Objects.equals(current.getChannelOrderNo(), channelOrderNo)) {
			return PayConstants.ALIPAY_SUCCESS;
		}
		return PayConstants.ALIPAY_FAIL;
	}

	private String duplicateWxRefundResponse(String refundOrderId, String channelRefundNo) {
		PayRefundOrder current = payRefundOrderMapper.selectById(refundOrderId);
		if (current != null && PayRefundOrderStatusEnum.STATUS_2.getCode().equals(current.getRefundStatus())
				&& Objects.equals(current.getChannelRefundNo(), channelRefundNo)) {
			return WxPayNotifyV3Response.success("成功");
		}
		return WxPayNotifyV3Response.fail("退款状态冲突");
	}

	private void saveNotifyRecord(String tenantId, String outTradeNo, String channelOrderNo, String request,
			String response, String type) {
		PayNotifyRecord notifyRecord = new PayNotifyRecord();
		notifyRecord.setTenantId(tenantId);
		notifyRecord.setOutTradeNo(outTradeNo);
		notifyRecord.setChannelOrderNo(channelOrderNo);
		notifyRecord.setRequest(request);
		notifyRecord.setResponse(response);
		notifyRecord.setType(type);
		this.save(notifyRecord);
	}

	private void sendPaySuccess(PayTradeOrder order) {
		JSONObject message = new JSONObject();
		message.put(PayConstants.OUT_TRADE_NO, order.getOutTradeNo());
		message.put(PayConstants.CHANNEL_ORDER_NO, order.getChannelOrderNo());
		message.put(PayConstants.PAY_SUCCESS_TIME, order.getPaySuccessTime());
		message.put(PayConstants.EXTRA_PARAMS, order.getExtra());
		message.put(PayConstants.TENANT_ID, order.getTenantId());
		assertMessageSent(rocketMQTemplate.syncSend(RocketMqConstants.PAY_NOTIFY_TOPIC, new GenericMessage<>(message),
				RocketMqConstants.TIME_OUT));
	}

	private void sendRefundSuccess(PayRefundOrder refundOrder) {
		JSONObject message = new JSONObject();
		message.put(PayConstants.EXTRA_PARAMS, refundOrder.getExtra());
		message.put(PayConstants.REFUND_TRADE_NO, refundOrder.getRefundTradeNo());
		message.put(PayConstants.TENANT_ID, refundOrder.getTenantId());
		assertMessageSent(rocketMQTemplate.syncSend(RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC, new GenericMessage<>(message),
				RocketMqConstants.TIME_OUT));
	}

	private void assertMessageSent(SendResult result) {
		if (result == null || !SendStatus.SEND_OK.equals(result.getSendStatus())) {
			throw new IllegalStateException("支付状态通知发送失败");
		}
	}

	private SignatureHeader buildSignatureHeader() {
		SignatureHeader signatureHeader = new SignatureHeader();
		signatureHeader.setSerial(httpServletRequest.getHeader("Wechatpay-Serial"));
		signatureHeader.setSignature(httpServletRequest.getHeader("Wechatpay-Signature"));
		signatureHeader.setNonce(httpServletRequest.getHeader("Wechatpay-Nonce"));
		signatureHeader.setTimeStamp(httpServletRequest.getHeader("Wechatpay-Timestamp"));
		return signatureHeader;
	}

}
