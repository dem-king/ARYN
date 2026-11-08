package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.entity.CallbackPrefixProperties;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.config.PayCallbackProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;

/**
 * 支付回调地址单一来源。路径中的身份版本只用于定位，验签与商户校验仍必须执行。
 */
@Component
@RequiredArgsConstructor
public class PayCallbackUrlBuilder {

	private final CallbackPrefixProperties callbackPrefixProperties;

	private final PayCallbackProperties properties;

	public String buildWxPayNotifyUrl(String baseUrl, String tenantId, String revisionId) {
		return build(baseUrl, "/notify/v2/wx/" + tenantId + "/" + revisionId);
	}

	public String buildWxRefundNotifyUrl(String baseUrl, String tenantId, String revisionId) {
		return build(baseUrl, "/notify/v2/refunds/wx/" + tenantId + "/" + revisionId);
	}

	public String buildAliPayNotifyUrl(String baseUrl, String tenantId, String revisionId) {
		return build(baseUrl, "/notify/v2/alipay/" + tenantId + "/" + revisionId);
	}

	private String build(String baseUrl, String path) {
		validateBase(baseUrl);
		String prefix = StringUtils.hasText(callbackPrefixProperties.getPay()) ? callbackPrefixProperties.getPay()
				: "pay";
		String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		return normalized + "/" + prefix + path;
	}

	void validateBase(String baseUrl) {
		if (!StringUtils.hasText(baseUrl)) {
			throw new ArynBusinessException("支付回调基础地址不能为空");
		}
		URI uri;
		try {
			uri = URI.create(baseUrl.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("支付回调基础地址不合法");
		}
		String scheme = uri.getScheme();
		if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
				|| !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null || uri.getQuery() != null
				|| uri.getFragment() != null) {
			throw new ArynBusinessException("支付回调基础地址不合法");
		}
		if (!properties.isAllowHttp() && !"https".equalsIgnoreCase(scheme)) {
			throw new ArynBusinessException("支付回调基础地址必须使用 HTTPS");
		}
		if (!properties.isAllowPrivate() && isPrivateHost(uri.getHost())) {
			throw new ArynBusinessException("支付回调基础地址不能使用本机或内网地址");
		}
	}

	private boolean isPrivateHost(String host) {
		String normalized = host.toLowerCase(java.util.Locale.ROOT);
		if ("localhost".equals(normalized) || normalized.endsWith(".localhost")) {
			return true;
		}
		try {
			InetAddress address = InetAddress.getByName(host);
			return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
					|| address.isLinkLocalAddress();
		}
		catch (Exception exception) {
			// 无法解析时交给渠道实际访问；不能因为本地 DNS 失败放宽结构校验
			return false;
		}
	}

}
