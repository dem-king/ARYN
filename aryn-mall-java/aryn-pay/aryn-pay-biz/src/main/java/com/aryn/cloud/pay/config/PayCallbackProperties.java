package com.aryn.cloud.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付回调地址策略。生产只允许公网 HTTPS。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aryn.pay.callback")
public class PayCallbackProperties {

	/**
	 * 本地/内网调试才允许 http；生产必须保持 false。
	 */
	private boolean allowHttp = false;

	/**
	 * 是否允许 localhost/内网地址；仅限隔离测试环境显式开启。
	 */
	private boolean allowPrivate = false;

}
