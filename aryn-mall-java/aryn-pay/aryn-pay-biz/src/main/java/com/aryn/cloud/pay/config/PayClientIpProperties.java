package com.aryn.cloud.pay.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付客户端 IP 取值策略。只有部署在受控代理之后才允许信任转发头。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aryn.pay.client-ip")
public class PayClientIpProperties {

	private boolean trustedProxy = false;

}
