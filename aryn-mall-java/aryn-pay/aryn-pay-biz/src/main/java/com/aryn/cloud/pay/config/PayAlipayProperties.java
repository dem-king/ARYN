package com.aryn.cloud.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝网关白名单。租户不能自定义网关地址。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aryn.pay.alipay")
public class PayAlipayProperties {

	private String gatewayProd = "https://openapi.alipay.com/gateway.do";

	private String gatewaySandbox = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

}
