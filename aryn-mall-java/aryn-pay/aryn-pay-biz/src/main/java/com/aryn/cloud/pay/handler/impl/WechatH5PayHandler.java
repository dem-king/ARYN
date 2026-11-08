package com.aryn.cloud.pay.handler.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request;
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result;
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PayPrepayResult;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayInvokeTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTerminalTypeEnum;
import com.aryn.cloud.pay.api.enums.PayTradeStatusEnum;
import com.aryn.cloud.pay.api.enums.PayTradeTypeEnum;
import com.aryn.cloud.pay.config.PayChannelClientFactory;
import com.aryn.cloud.pay.handler.AbstractPayOrderHandler;
import com.aryn.cloud.pay.service.IPayTradeOrderService;
import com.aryn.cloud.pay.service.PayCallbackUrlBuilder;
import com.aryn.cloud.pay.service.PayClientIpResolver;
import com.aryn.cloud.pay.service.PayCredentialResolver;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 微信外部浏览器 H5 支付。微信内网页应走 JSAPI，不能复用本 Handler。
 */
@Service("WX_H5_PAY")
@RequiredArgsConstructor
public class WechatH5PayHandler extends AbstractPayOrderHandler {

	private final IPayTradeOrderService payTradeOrderService;

	private final PayCallbackUrlBuilder callbackUrlBuilder;

	private final PayCredentialResolver credentialResolver;

	private final PayChannelClientFactory clientFactory;

	private final PayClientIpResolver clientIpResolver;

	private final HttpServletRequest httpServletRequest;

	@Override
	public PayPrepayResult doPay(PayTradeOrder payTradeOrder) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		PayResolvedIdentity identity = credentialResolver.resolveForOrder(tenantId, payTradeOrder);
		String notifyUrl = callbackUrlBuilder.buildWxPayNotifyUrl(payTradeOrder.getNotifyUrl(), tenantId,
				payTradeOrder.getConfigRevisionId());

		WxPayUnifiedOrderV3Request request = new WxPayUnifiedOrderV3Request();
		request.setOutTradeNo(payTradeOrder.getOutTradeNo());
		request.setDescription(payTradeOrder.getDescription());
		request.setNotifyUrl(notifyUrl);
		WxPayUnifiedOrderV3Request.Amount amount = new WxPayUnifiedOrderV3Request.Amount();
		amount.setTotal(payTradeOrder.getAmount().multiply(BigDecimal.valueOf(100)).intValue());
		amount.setCurrency(PayConstants.CURRENCY);
		request.setAmount(amount);

		WxPayUnifiedOrderV3Request.H5Info h5Info = new WxPayUnifiedOrderV3Request.H5Info();
		h5Info.setType("Wap");
		h5Info.setAppName("悦航购");
		if (StringUtils.hasText(payTradeOrder.getReturnUrl())) {
			h5Info.setAppUrl(payTradeOrder.getReturnUrl());
		}
		WxPayUnifiedOrderV3Request.SceneInfo sceneInfo = new WxPayUnifiedOrderV3Request.SceneInfo();
		sceneInfo.setPayerClientIp(clientIpResolver.resolve(httpServletRequest));
		sceneInfo.setH5Info(h5Info);
		request.setSceneInfo(sceneInfo);

		WxPayService wxPayService = clientFactory.wxPayService(identity);
		try {
			WxPayUnifiedOrderV3Result result = wxPayService.createOrderV3(TradeTypeEnum.H5, request);
			if (result == null || !StringUtils.hasText(result.getH5Url())) {
				throw new ArynBusinessException("微信H5支付链接为空");
			}
			String redirectUrl = result.getH5Url();
			if (StringUtils.hasText(payTradeOrder.getReturnUrl())) {
				redirectUrl = redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "redirect_url="
						+ URLEncoder.encode(payTradeOrder.getReturnUrl(), StandardCharsets.UTF_8);
			}
			return PayPrepayResult.of(PayChannelEnum.WECHAT.getCode(), PayTerminalTypeEnum.H5.getCode(),
					PayInvokeTypeEnum.REDIRECT, payTradeOrder.getOutTradeNo(),
					Map.of("redirectUrl", redirectUrl));
		}
		catch (WxPayException e) {
			throw new ArynBusinessException(e.getReturnMsg());
		}
	}

	@Override
	public PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO) {
		PayTradeOrder payTradeOrder = payTradeOrderService.getOne(Wrappers.<PayTradeOrder>lambdaQuery()
			.eq(PayTradeOrder::getOutTradeNo, createOrderReqDTO.getOutTradeNo()).last("LIMIT 1"));
		if (null != payTradeOrder) {
			return payTradeOrder;
		}
		PayResolvedIdentity identity = credentialResolver.resolveNewPayment(PayChannelEnum.WECHAT.getCode(),
				PayTerminalTypeEnum.H5.getCode());
		payTradeOrder = new PayTradeOrder();
		payTradeOrder.setPayStatus(CommonConstants.NO);
		payTradeOrder.setTradeStatus(PayTradeStatusEnum.PENDING.name());
		payTradeOrder.setTenantId(ArynTenantContextHolder.getTenantId());
		payTradeOrder.setDescription(createOrderReqDTO.getSubject());
		payTradeOrder.setOutTradeNo(createOrderReqDTO.getOutTradeNo());
		payTradeOrder.setChannelMchId(identity.getMchId());
		payTradeOrder.setTradeType(PayTradeTypeEnum.WX_H5_PAY.getName());
		payTradeOrder.setAmount(new BigDecimal(createOrderReqDTO.getTotalAmount()));
		payTradeOrder.setNotifyUrl(createOrderReqDTO.getNotifyUrl());
		payTradeOrder.setReturnUrl(createOrderReqDTO.getReturnUrl());
		payTradeOrder.setQuitUrl(createOrderReqDTO.getQuitUrl());
		payTradeOrder.setExtra(createOrderReqDTO.getExtra());
		payTradeOrder.setTerminalType(PayTerminalTypeEnum.H5.getCode());
		payTradeOrder.setUserId(createOrderReqDTO.getUserId());
		payTradeOrder.setConfigRevisionId(identity.getRevisionId());
		payTradeOrderService.save(payTradeOrder);
		return payTradeOrder;
	}

}
