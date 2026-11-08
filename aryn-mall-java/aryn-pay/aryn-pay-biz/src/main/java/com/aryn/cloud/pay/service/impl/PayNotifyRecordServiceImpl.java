
package com.aryn.cloud.pay.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.binarywang.wxpay.bean.notify.SignatureHeader;
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.entity.PayNotifyRecord;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.enums.PayRefundOrderStatusEnum;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.mapper.PayNotifyRecordMapper;
import com.aryn.cloud.pay.mapper.PayRefundOrderMapper;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.IPayNotifyRecordService;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayEventOutboxService;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayNotifyAck;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.aryn.cloud.pay.service.PayTradeTransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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

	private final PayEventOutboxService outboxService;

	private final PayTradeOrderMapper payTradeOrderMapper;

	private final PayRefundOrderMapper payRefundOrderMapper;

	private final HttpServletRequest httpServletRequest;

	private final IPayConfigService payConfigService;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	private final PayTradeTransactionService tradeTransactionService;

	private final PayMaterialParser materialParser;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck wxPayNotify(String tenantId, String terminalType, String notifyData) {
		try {
			return wxPayNotifyV2(tenantId, activeRevision(PayConstants.PAY_TYPE_1, terminalType), notifyData);
		}
		catch (ArynBusinessException exception) {
			log.warn("旧版微信支付回调无法定位支付身份, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return PayNotifyAck.failure(exception.getMsg());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck aliPayNotify(String tenantId, String terminalType, HttpServletRequest request) {
		try {
			return aliPayNotifyV2(tenantId, activeRevision(PayConstants.PAY_TYPE_2, terminalType), request);
		}
		catch (ArynBusinessException exception) {
			log.warn("旧版支付宝回调无法定位支付身份, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return PayNotifyAck.failure(exception.getMsg());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck wxPayRefundNotify(String tenantId, String terminalType, String notifyData) {
		try {
			return wxPayRefundNotifyV2(tenantId, activeRevision(PayConstants.PAY_TYPE_1, terminalType), notifyData);
		}
		catch (ArynBusinessException exception) {
			log.warn("旧版微信退款回调无法定位支付身份, tenantId: {}, terminalType: {}", tenantId, terminalType);
			return PayNotifyAck.failure(exception.getMsg());
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck wxPayNotifyV2(String tenantId, String revisionId, String notifyData) {
		PayResolvedIdentity signatureIdentity = null;
		WxPayNotifyV3Result wxPayOrderNotifyV3Result = null;
		try {
			for (PayResolvedIdentity candidate : credentialResolver.resolveForSignature(tenantId, revisionId)) {
				try {
					wxPayOrderNotifyV3Result = parseWxPayNotify(candidate, notifyData);
					signatureIdentity = candidate;
					break;
				}
				catch (WxPayException exception) {
					// 尝试同身份的下一个有效材料版本
				}
			}
		}
		catch (ArynBusinessException exception) {
			return PayNotifyAck.failure(exception.getMsg());
		}
		if (wxPayOrderNotifyV3Result == null || signatureIdentity == null) {
			log.warn("微信支付回调验签或解密失败, tenantId: {}, revisionId: {}", tenantId, revisionId);
			return PayNotifyAck.failure("微信支付回调验签失败");
		}
		WxPayNotifyV3Result.DecryptNotifyResult decryptNotifyResult = wxPayOrderNotifyV3Result.getResult();
		if (decryptNotifyResult == null || !StringUtils.hasText(decryptNotifyResult.getOutTradeNo())) {
			return PayNotifyAck.failure("回调订单号为空");
		}
		String outTradeNo = decryptNotifyResult.getOutTradeNo();
		PayTradeOrder orderInfo = findTradeOrder(outTradeNo, signatureIdentity.getTerminalType(), "WX_");
		if (ObjectUtil.isNull(orderInfo)) {
			return PayNotifyAck.failure("order not found! orderNo: " + outTradeNo);
		}
		PayResolvedIdentity orderIdentity;
		try {
			orderIdentity = credentialResolver.resolveForOrder(tenantId, orderInfo);
			assertPathIdentityMatches(tenantId, revisionId, orderIdentity);
		}
		catch (ArynBusinessException exception) {
			log.warn("微信支付回调身份校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return PayNotifyAck.failure(exception.getMsg());
		}
		if (!validWxPayNotification(decryptNotifyResult, orderInfo, orderIdentity)) {
			log.warn("微信支付回调业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return PayNotifyAck.failure("回调数据校验失败");
		}
		LocalDateTime paySuccessTime = parseOffsetDateTime(decryptNotifyResult.getSuccessTime());
		if (paySuccessTime == null) {
			return PayNotifyAck.failure("支付时间格式错误");
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
				JSON.toJSONString(decryptNotifyResult), "success", PayConstants.PAY_NOTIFY_TYPE);
		sendPaySuccess(orderInfo);
		return PayNotifyAck.ok();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck aliPayNotifyV2(String tenantId, String revisionId, HttpServletRequest request) {
		Map<String, String> params = extractParams(request);
		PayResolvedIdentity signatureIdentity = null;
		try {
			for (PayResolvedIdentity candidate : credentialResolver.resolveForSignature(tenantId, revisionId)) {
				if (verifyAlipaySignature(params, candidate.material(PayConfigMaterialPurposeEnum.ALI_PUBLIC_CERT))) {
					signatureIdentity = candidate;
					break;
				}
			}
		}
		catch (AlipayApiException | ArynBusinessException exception) {
			log.warn("支付宝回调验签异常, tenantId: {}, revisionId: {}", tenantId, revisionId);
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		if (signatureIdentity == null) {
			log.warn("支付宝回调验签失败, tenantId: {}, revisionId: {}", tenantId, revisionId);
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		String outTradeNo = params.get("out_trade_no");
		String tradeStatus = params.get("trade_status");
		if (!StringUtils.hasText(outTradeNo)) {
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
			return PayNotifyAck.ok();
		}
		PayTradeOrder orderInfo = findTradeOrder(outTradeNo, signatureIdentity.getTerminalType(), "ALI_");
		if (orderInfo == null) {
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		PayResolvedIdentity orderIdentity;
		try {
			orderIdentity = credentialResolver.resolveForOrder(tenantId, orderInfo);
			assertPathIdentityMatches(tenantId, revisionId, orderIdentity);
		}
		catch (ArynBusinessException exception) {
			log.warn("支付宝回调身份校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		if (!validAlipayNotification(params, orderInfo, orderIdentity)) {
			log.warn("支付宝回调业务字段校验失败, tenantId: {}, orderNo: {}", tenantId, outTradeNo);
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		String channelOrderNo = params.get("trade_no");
		LocalDateTime paySuccessTime;
		try {
			paySuccessTime = LocalDateTime.parse(params.get("gmt_payment"),
					DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		}
		catch (RuntimeException exception) {
			return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
		}
		int updated = payTradeOrderMapper.markPaidIfPending(tenantId, orderInfo.getId(), channelOrderNo,
				paySuccessTime);
		if (updated == 0) {
			return duplicateAlipayResponse(orderInfo.getId(), channelOrderNo);
		}
		orderInfo.setChannelOrderNo(channelOrderNo);
		orderInfo.setPayStatus(CommonConstants.YES);
		orderInfo.setPaySuccessTime(paySuccessTime);
		saveNotifyRecord(tenantId, outTradeNo, channelOrderNo, JSON.toJSONString(params), PayConstants.ALIPAY_SUCCESS,
				PayConstants.PAY_NOTIFY_TYPE);
		sendPaySuccess(orderInfo);
		return PayNotifyAck.ok();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PayNotifyAck wxPayRefundNotifyV2(String tenantId, String revisionId, String notifyData) {
		PayResolvedIdentity signatureIdentity = null;
		WxPayRefundNotifyV3Result refundNotifyResult = null;
		try {
			for (PayResolvedIdentity candidate : credentialResolver.resolveForSignature(tenantId, revisionId)) {
				try {
					refundNotifyResult = parseWxRefundNotify(candidate, notifyData);
					signatureIdentity = candidate;
					break;
				}
				catch (WxPayException exception) {
					// 尝试同身份的下一个有效材料版本
				}
			}
		}
		catch (ArynBusinessException exception) {
			return PayNotifyAck.failure(exception.getMsg());
		}
		if (refundNotifyResult == null || signatureIdentity == null) {
			log.warn("微信退款回调验签或解密失败, tenantId: {}, revisionId: {}", tenantId, revisionId);
			return PayNotifyAck.failure("微信退款回调验签失败");
		}
		WxPayRefundNotifyV3Result.DecryptNotifyResult result = refundNotifyResult.getResult();
		if (result == null || !StringUtils.hasText(result.getOutRefundNo())) {
			return PayNotifyAck.failure("回调退款单号为空");
		}
		PayRefundOrder payRefundOrder = payRefundOrderMapper.selectOne(
				Wrappers.<PayRefundOrder>lambdaQuery().eq(PayRefundOrder::getRefundTradeNo, result.getOutRefundNo()));
		if (ObjectUtil.isNull(payRefundOrder)) {
			return PayNotifyAck.failure("order not found! orderNo: " + result.getOutRefundNo());
		}
		PayTradeOrder tradeOrder = payTradeOrderMapper.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, result.getOutTradeNo())
			.last("LIMIT 1"));
		if (tradeOrder == null) {
			return PayNotifyAck.failure("原支付单不存在");
		}
		PayResolvedIdentity orderIdentity;
		try {
			orderIdentity = credentialResolver.resolveForOrder(tenantId, tradeOrder);
			assertPathIdentityMatches(tenantId, revisionId, orderIdentity);
		}
		catch (ArynBusinessException exception) {
			log.warn("微信退款回调身份校验失败, tenantId: {}, refundNo: {}", tenantId, result.getOutRefundNo());
			return PayNotifyAck.failure(exception.getMsg());
		}
		if (!validWxRefundNotification(result, payRefundOrder, orderIdentity)) {
			log.warn("微信退款回调业务字段校验失败, tenantId: {}, refundNo: {}", tenantId, result.getOutRefundNo());
			return PayNotifyAck.failure("回调数据校验失败");
		}
		LocalDateTime refundSuccessTime = parseOffsetDateTime(result.getSuccessTime());
		if (refundSuccessTime == null) {
			return PayNotifyAck.failure("退款时间格式错误");
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
				"success", PayConstants.REFUND_NOTIFY_TYPE);
		sendRefundSuccess(payRefundOrder);
		return PayNotifyAck.ok();
	}

	/**
	 * 主动向渠道查单并落账：与回调共用同一段落账+事件写入逻辑。
	 */
	@Override
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
		if (CommonConstants.YES.equals(orderInfo.getPayStatus())) {
			return PaySettlementResult.paid(orderInfo.getChannelOrderNo());
		}
		if (!StringUtils.hasText(orderInfo.getTradeType())) {
			return PaySettlementResult.unpaid();
		}
		if (orderInfo.getTradeType().startsWith("WX_")) {
			return queryAndSettleWxPayOrder(tenantId, orderInfo);
		}
		if (orderInfo.getTradeType().startsWith("ALI_")) {
			return queryAndSettleAlipayOrder(tenantId, orderInfo);
		}
		return PaySettlementResult.unpaid();
	}

	private PaySettlementResult queryAndSettleWxPayOrder(String tenantId, PayTradeOrder orderInfo) {
		PayResolvedIdentity identity;
		try {
			identity = credentialResolver.resolveForOrder(tenantId, orderInfo);
		}
		catch (ArynBusinessException exception) {
			log.warn("微信查单无法解析原交易身份, tenantId: {}, orderNo: {}", tenantId, orderInfo.getOutTradeNo());
			return PaySettlementResult.unpaid();
		}
		WxPayOrderQueryV3Result queryResult;
		try {
			queryResult = queryWxOrder(identity, orderInfo.getOutTradeNo());
		}
		catch (Exception e) {
			log.warn("微信查单失败, tenantId: {}, orderNo: {}, msg: {}", tenantId, orderInfo.getOutTradeNo(),
					e.getMessage());
			return PaySettlementResult.unpaid();
		}
		return tradeTransactionService.settleWxQuery(tenantId, orderInfo, identity, queryResult);
	}

	private PaySettlementResult queryAndSettleAlipayOrder(String tenantId, PayTradeOrder orderInfo) {
		PayResolvedIdentity identity;
		try {
			identity = credentialResolver.resolveForOrder(tenantId, orderInfo);
		}
		catch (ArynBusinessException exception) {
			log.warn("支付宝查单无法解析原交易身份, tenantId: {}, orderNo: {}", tenantId, orderInfo.getOutTradeNo());
			return PaySettlementResult.unpaid();
		}
		AlipayTradeQueryResponse queryResult;
		try {
			queryResult = queryAlipayOrder(identity, orderInfo.getOutTradeNo());
		}
		catch (Exception exception) {
			log.warn("支付宝查单失败, tenantId: {}, orderNo: {}, msg: {}", tenantId, orderInfo.getOutTradeNo(),
					exception.getMessage());
			return PaySettlementResult.unpaid();
		}
		if (!matchesAlipayBuyer(orderInfo, identity, queryResult == null ? null : queryResult.getBuyerUserId(),
				queryResult == null ? null : queryResult.getBuyerOpenId())) {
			log.warn("支付宝查单买家身份与订单不一致, tenantId: {}, orderNo: {}", tenantId, orderInfo.getOutTradeNo());
			return PaySettlementResult.unpaid();
		}
		return tradeTransactionService.settleAlipayQuery(tenantId, orderInfo, queryResult);
	}

	/**
	 * 取消业务订单前的支付门禁：先查单，确认未支付再关单；任何不确定都返回 UNKNOWN。
	 */
	@Override
	public PayCloseResult closeAndCheckPayOrder(String outTradeNo) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(outTradeNo)) {
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "缺少租户或订单号");
		}
		PayTradeOrder order = payTradeOrderMapper.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, outTradeNo)
			.last("LIMIT 1"));
		if (order == null) {
			return PayCloseResult.of(PayCloseResult.STATUS_NOT_INITIATED, null, "未发起支付");
		}
		// 短事务抢占关单意图；此后所有渠道调用都在事务外
		PayTradeTransactionService.CloseClaim claim = tradeTransactionService.claimClose(tenantId, order.getId());
		if (claim.decision() == PayTradeTransactionService.CloseDecision.PAID) {
			return PayCloseResult.of(PayCloseResult.STATUS_PAID, "SUCCESS", claim.message());
		}
		if (claim.decision() == PayTradeTransactionService.CloseDecision.CLOSED) {
			return PayCloseResult.of(PayCloseResult.STATUS_CLOSED, "CLOSED", claim.message());
		}
		if (claim.decision() == PayTradeTransactionService.CloseDecision.UNKNOWN) {
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, claim.message());
		}
		if (order.getTradeType() != null && order.getTradeType().startsWith("WX_")) {
			return closeWxPayOrder(tenantId, order);
		}
		if (order.getTradeType() != null && order.getTradeType().startsWith("ALI_")) {
			return closeAlipayOrder(tenantId, order);
		}
		tradeTransactionService.markUnknown(tenantId, order.getId(), null, "不支持的支付渠道");
		return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "不支持的支付渠道");
	}

	private PayCloseResult closeWxPayOrder(String tenantId, PayTradeOrder order) {
		PayResolvedIdentity identity;
		try {
			identity = credentialResolver.resolveForOrder(tenantId, order);
		}
		catch (ArynBusinessException exception) {
			tradeTransactionService.markUnknown(tenantId, order.getId(), null, "无法解析原交易身份");
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "无法解析原交易身份");
		}
		try {
			WxPayOrderQueryV3Result queryResult = queryWxOrder(identity, order.getOutTradeNo());
			String tradeState = queryResult == null ? null : queryResult.getTradeState();
			if ("SUCCESS".equals(tradeState)) {
				PaySettlementResult settled = tradeTransactionService.settleWxQuery(tenantId, order, identity,
						queryResult);
				if (settled.isPaid()) {
					return PayCloseResult.of(PayCloseResult.STATUS_PAID, "SUCCESS", "订单已支付");
				}
				tradeTransactionService.markUnknown(tenantId, order.getId(), null, "渠道已支付但业务校验未通过");
				return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, tradeState, "渠道已支付但业务校验未通过");
			}
			if ("CLOSED".equals(tradeState) || "REVOKED".equals(tradeState) || "PAYERROR".equals(tradeState)) {
				tradeTransactionService.markClosed(tenantId, order.getId());
				return PayCloseResult.of(PayCloseResult.STATUS_CLOSED, tradeState, "渠道已关闭");
			}
			return closeWxChannel(tenantId, order, identity, tradeState);
		}
		catch (WxPayException exception) {
			// 查单阶段失败：不能假定未支付，也不能释放库存
			log.warn("微信取消前查单失败, tenantId: {}, orderNo: {}, msg: {}", tenantId, order.getOutTradeNo(),
					exception.getMessage());
			tradeTransactionService.markUnknown(tenantId, order.getId(), exception.getErrCode(),
					exception.getMessage());
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "支付结果暂时无法确认");
		}
	}

	private PayCloseResult closeWxChannel(String tenantId, PayTradeOrder order, PayResolvedIdentity identity,
			String tradeState) {
		try {
			clientFactory.wxPayService(identity).closeOrderV3(order.getOutTradeNo());
			tradeTransactionService.markClosed(tenantId, order.getId());
			return PayCloseResult.of(PayCloseResult.STATUS_CLOSED, tradeState, "支付单已关闭");
		}
		catch (WxPayException exception) {
			String errorCode = exception.getErrCode();
			if ("ORDERNOTEXIST".equals(errorCode) || "ORDER_CLOSED".equals(errorCode)) {
				tradeTransactionService.markClosed(tenantId, order.getId());
				return PayCloseResult.of(PayCloseResult.STATUS_CLOSED, tradeState, "支付单已关闭");
			}
			tradeTransactionService.markUnknown(tenantId, order.getId(), errorCode, exception.getMessage());
			log.warn("微信关单失败, tenantId: {}, orderNo: {}, code: {}", tenantId, order.getOutTradeNo(), errorCode);
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, tradeState, "支付关单未确认");
		}
	}

	private PayCloseResult closeAlipayOrder(String tenantId, PayTradeOrder order) {
		PayResolvedIdentity identity;
		try {
			identity = credentialResolver.resolveForOrder(tenantId, order);
		}
		catch (ArynBusinessException exception) {
			tradeTransactionService.markUnknown(tenantId, order.getId(), null, "无法解析原交易身份");
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "无法解析原交易身份");
		}
		try {
			AlipayTradeQueryResponse queryResult = queryAlipayOrder(identity, order.getOutTradeNo());
			if (queryResult != null && queryResult.isSuccess()) {
				String tradeStatus = queryResult.getTradeStatus();
				if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
					if (!matchesAlipayBuyer(order, identity, queryResult.getBuyerUserId(),
							queryResult.getBuyerOpenId())) {
						tradeTransactionService.markUnknown(tenantId, order.getId(), null, "渠道买家身份与订单不一致");
						return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, tradeStatus,
								"渠道已支付但买家身份校验未通过");
					}
					PaySettlementResult settled = tradeTransactionService.settleAlipayQuery(tenantId, order,
							queryResult);
					if (settled.isPaid()) {
						return PayCloseResult.of(PayCloseResult.STATUS_PAID, tradeStatus, "订单已支付");
					}
					tradeTransactionService.markUnknown(tenantId, order.getId(), null, "渠道已支付但业务校验未通过");
					return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, tradeStatus,
							"渠道已支付但业务校验未通过");
				}
				if ("TRADE_CLOSED".equals(tradeStatus)) {
					tradeTransactionService.markClosed(tenantId, order.getId());
					return PayCloseResult.of(PayCloseResult.STATUS_CLOSED, tradeStatus, "支付宝交易已关闭");
				}
			}
			return closeAlipayChannel(tenantId, order, identity, queryResult);
		}
		catch (Exception exception) {
			log.warn("支付宝取消前查单失败, tenantId: {}, orderNo: {}, msg: {}", tenantId, order.getOutTradeNo(),
					exception.getMessage());
			tradeTransactionService.markUnknown(tenantId, order.getId(), null, exception.getMessage());
			return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, null, "支付结果暂时无法确认");
		}
	}

	private PayCloseResult closeAlipayChannel(String tenantId, PayTradeOrder order, PayResolvedIdentity identity,
			AlipayTradeQueryResponse queryResult) throws AlipayApiException {
		AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", order.getOutTradeNo());
		request.setBizContent(bizContent.toJSONString());
		// 证书模式必须走 certificateExecute；普通 execute 在 alipayRootCertSN 非空时会直接报错
		AlipayTradeCloseResponse response = clientFactory.alipayClient(identity).certificateExecute(request);
		if (response != null && (response.isSuccess()
				|| "ACQ.TRADE_NOT_EXIST".equals(response.getSubCode())
				|| "ACQ.TRADE_STATUS_ERROR".equals(response.getSubCode()))) {
			tradeTransactionService.markClosed(tenantId, order.getId());
			return PayCloseResult.of(PayCloseResult.STATUS_CLOSED,
					queryResult == null ? null : queryResult.getTradeStatus(), "支付宝交易已关闭");
		}
		tradeTransactionService.markUnknown(tenantId, order.getId(),
				response == null ? null : response.getSubCode(), response == null ? null : response.getSubMsg());
		return PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN,
				queryResult == null ? null : queryResult.getTradeStatus(), "支付宝关单未确认");
	}

	protected WxPayOrderQueryV3Result queryWxOrder(PayResolvedIdentity identity, String outTradeNo)
			throws WxPayException {
		return clientFactory.wxPayService(identity).queryOrderV3(null, outTradeNo);
	}

	protected AlipayTradeQueryResponse queryAlipayOrder(PayResolvedIdentity identity, String outTradeNo)
			throws AlipayApiException {
		AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
		JSONObject bizContent = new JSONObject();
		bizContent.put("out_trade_no", outTradeNo);
		request.setBizContent(bizContent.toJSONString());
		// 证书模式必须走 certificateExecute；普通 execute 在 alipayRootCertSN 非空时会直接报错
		return clientFactory.alipayClient(identity).certificateExecute(request);
	}

	protected WxPayNotifyV3Result parseWxPayNotify(PayResolvedIdentity identity, String notifyData)
			throws WxPayException {
		return clientFactory.wxPayService(identity).parseOrderNotifyV3Result(notifyData, buildSignatureHeader());
	}

	protected WxPayRefundNotifyV3Result parseWxRefundNotify(PayResolvedIdentity identity, String notifyData)
			throws WxPayException {
		return clientFactory.wxPayService(identity).parseRefundNotifyV3Result(notifyData, buildSignatureHeader());
	}

	protected boolean verifyAlipaySignature(Map<String, String> params, byte[] alipayPublicCert)
			throws AlipayApiException {
		if (alipayPublicCert == null || alipayPublicCert.length == 0) {
			return false;
		}
		X509Certificate certificate = materialParser.parseCertificate(alipayPublicCert);
		String publicKey = Base64.getEncoder().encodeToString(certificate.getPublicKey().getEncoded());
		return AlipaySignature.rsaCheckV1(params, publicKey, "UTF-8", "RSA2");
	}

	boolean validWxPayNotification(WxPayNotifyV3Result.DecryptNotifyResult result, PayTradeOrder order,
			PayResolvedIdentity identity) {
		return identity != null && result.getAmount() != null && "SUCCESS".equals(result.getTradeState())
				&& Objects.equals(identity.getAppId(), result.getAppid())
				&& Objects.equals(identity.getMchId(), result.getMchid())
				&& PayConstants.CURRENCY.equals(result.getAmount().getCurrency())
				&& matchesCents(order.getAmount(), result.getAmount().getTotal())
				&& StringUtils.hasText(result.getTransactionId()) && StringUtils.hasText(result.getSuccessTime());
	}

	boolean validAlipayNotification(Map<String, String> params, PayTradeOrder order, PayResolvedIdentity identity) {
		if (identity == null || !Objects.equals(identity.getAppId(), params.get("app_id"))
				|| !matchesAmount(order.getAmount(), params.get("total_amount"))
				|| !StringUtils.hasText(params.get("trade_no")) || !StringUtils.hasText(params.get("gmt_payment"))) {
			return false;
		}
		return StringUtils.hasText(identity.getMchId()) && Objects.equals(identity.getMchId(), params.get("seller_id"))
				&& matchesAlipayBuyer(order, identity, params.get("buyer_id"), params.get("buyer_open_id"));
	}

	/**
	 * 支付宝小程序订单的买家字段必须与冻结身份模式一致。渠道未回传买家字段时
	 * 仍以验签/商户/金额为权威；一旦回传就必须与订单买家一致，防止跨账号落账。
	 */
	private boolean matchesAlipayBuyer(PayTradeOrder order, PayResolvedIdentity identity, String buyerUserId,
			String buyerOpenId) {
		if (identity == null || !StringUtils.hasText(order.getOpenId())) {
			return true;
		}
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(identity.getBuyerIdentityMode());
		if (mode == null) {
			return true;
		}
		String actual = mode == MiniAppIdentityModeEnum.USER_ID ? buyerUserId : buyerOpenId;
		return !StringUtils.hasText(actual) || order.getOpenId().equals(actual);
	}

	boolean validWxRefundNotification(WxPayRefundNotifyV3Result.DecryptNotifyResult result,
			PayRefundOrder refundOrder, PayResolvedIdentity identity) {
		return identity != null && result.getAmount() != null && "SUCCESS".equals(result.getRefundStatus())
				&& Objects.equals(identity.getMchId(), result.getMchid())
				&& Objects.equals(refundOrder.getOutTradeNo(), result.getOutTradeNo())
				&& matchesCents(refundOrder.getPayAmount(), result.getAmount().getTotal())
				&& matchesCents(refundOrder.getRefundAmount(), result.getAmount().getRefund())
				&& StringUtils.hasText(result.getRefundId()) && StringUtils.hasText(result.getSuccessTime());
	}

	private void assertPathIdentityMatches(String tenantId, String revisionId, PayResolvedIdentity orderIdentity) {
		PayConfigRevision pathRevision = credentialResolver.revision(tenantId, revisionId);
		if (!credentialResolver.sameIdentity(pathRevision, orderIdentity)) {
			throw new ArynBusinessException("回调身份与订单冻结身份不一致");
		}
	}

	private String activeRevision(String channel, String terminalType) {
		PayConfigVO config = payConfigService.getConfig(channel, terminalType);
		if (config == null || !StringUtils.hasText(config.getActiveRevisionId())) {
			throw new ArynBusinessException("支付配置未完成身份版本迁移，暂不能处理回调");
		}
		return config.getActiveRevisionId();
	}

	private PayTradeOrder findTradeOrder(String outTradeNo, String terminalType, String tradeTypePrefix) {
		return payTradeOrderMapper.selectOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, outTradeNo)
			.eq(PayTradeOrder::getTerminalType, terminalType)
			.likeRight(PayTradeOrder::getTradeType, tradeTypePrefix)
			.last("LIMIT 1"));
	}

	private Map<String, String> extractParams(HttpServletRequest request) {
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
		return params;
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

	private PayNotifyAck duplicateWxPayResponse(String orderId, String channelOrderNo) {
		PayTradeOrder current = payTradeOrderMapper.selectById(orderId);
		if (current != null && CommonConstants.YES.equals(current.getPayStatus())
				&& Objects.equals(current.getChannelOrderNo(), channelOrderNo)) {
			return PayNotifyAck.ok();
		}
		return PayNotifyAck.failure("支付状态冲突");
	}

	private PayNotifyAck duplicateAlipayResponse(String orderId, String channelOrderNo) {
		PayTradeOrder current = payTradeOrderMapper.selectById(orderId);
		if (current != null && CommonConstants.YES.equals(current.getPayStatus())
				&& Objects.equals(current.getChannelOrderNo(), channelOrderNo)) {
			return PayNotifyAck.ok();
		}
		return PayNotifyAck.failure(PayConstants.ALIPAY_FAIL);
	}

	private PayNotifyAck duplicateWxRefundResponse(String refundOrderId, String channelRefundNo) {
		PayRefundOrder current = payRefundOrderMapper.selectById(refundOrderId);
		if (current != null && PayRefundOrderStatusEnum.STATUS_2.getCode().equals(current.getRefundStatus())
				&& Objects.equals(current.getChannelRefundNo(), channelRefundNo)) {
			return PayNotifyAck.ok();
		}
		return PayNotifyAck.failure("退款状态冲突");
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
		message.put(PayConstants.TENANT_ID, tenantIdOf(order.getTenantId()));
		outboxService.enqueue(tenantIdOf(order.getTenantId()), "PAY_SUCCESS", order.getOutTradeNo(),
				RocketMqConstants.PAY_NOTIFY_TOPIC, message.toJSONString());
	}

	private void sendRefundSuccess(PayRefundOrder refundOrder) {
		JSONObject message = new JSONObject();
		message.put(PayConstants.EXTRA_PARAMS, refundOrder.getExtra());
		message.put(PayConstants.REFUND_TRADE_NO, refundOrder.getRefundTradeNo());
		message.put(PayConstants.TENANT_ID, tenantIdOf(refundOrder.getTenantId()));
		outboxService.enqueue(tenantIdOf(refundOrder.getTenantId()), "REFUND_SUCCESS", refundOrder.getRefundTradeNo(),
				RocketMqConstants.PAY_REFUND_NOTIFY_TOPIC, message.toJSONString());
	}

	private String tenantIdOf(String tenantId) {
		return StringUtils.hasText(tenantId) ? tenantId : ArynTenantContextHolder.getTenantId();
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
