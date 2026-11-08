package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.config.PayClientIpProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信 H5 场景 payer_client_ip：默认只取 remoteAddr；
 * 只有显式信任受控代理时才读取转发头，避免客户端伪造 IP。
 */
@Component
@RequiredArgsConstructor
public class PayClientIpResolver {

	private final PayClientIpProperties properties;

	public String resolve(HttpServletRequest request) {
		if (request == null) {
			throw new ArynBusinessException("无法获取支付客户端IP");
		}
		String ip = properties.isTrustedProxy() ? IpUtils.getClientIp(request) : request.getRemoteAddr();
		if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
			throw new ArynBusinessException("无法获取支付客户端IP");
		}
		return ip.trim();
	}

}
