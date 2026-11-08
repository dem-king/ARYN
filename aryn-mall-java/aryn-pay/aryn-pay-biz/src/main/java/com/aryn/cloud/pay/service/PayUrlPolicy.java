package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 同步跳转/退出地址白名单。前端可传，但只能回到商城可信站点，不能变成开放重定向。
 */
@Component
@RequiredArgsConstructor
public class PayUrlPolicy {

	@Value("${aryn.pay.allowed-return-hosts:}")
	private String allowedReturnHosts;

	public void validate(String notifyUrl, String returnUrl, String quitUrl) {
		if (!StringUtils.hasText(returnUrl) && !StringUtils.hasText(quitUrl)) {
			return;
		}
		Set<String> allowed = parseAllowed();
		String notifyHost = host(notifyUrl);
		check(returnUrl, notifyHost, allowed);
		check(quitUrl, notifyHost, allowed);
	}

	private void check(String value, String notifyHost, Set<String> allowed) {
		if (!StringUtils.hasText(value)) {
			return;
		}
		String host = host(value);
		if (!host.equalsIgnoreCase(notifyHost) && !allowed.contains(host.toLowerCase(java.util.Locale.ROOT))) {
			throw new ArynBusinessException("支付跳转地址不在允许的商城域名内");
		}
	}

	private String host(String value) {
		if (!StringUtils.hasText(value)) {
			throw new ArynBusinessException("支付跳转地址不合法");
		}
		try {
			String host = URI.create(value).getHost();
			if (!StringUtils.hasText(host)) {
				throw new ArynBusinessException("支付跳转地址不合法");
			}
			return host;
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("支付跳转地址不合法");
		}
	}

	private Set<String> parseAllowed() {
		if (!StringUtils.hasText(allowedReturnHosts)) {
			return Set.of();
		}
		return Arrays.stream(allowedReturnHosts.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(host -> host.toLowerCase(java.util.Locale.ROOT))
			.collect(Collectors.toSet());
	}

}
