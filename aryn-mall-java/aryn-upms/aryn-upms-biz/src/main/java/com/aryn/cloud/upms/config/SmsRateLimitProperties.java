package com.aryn.cloud.upms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信发送频控配置。
 *
 * <p>IP 维度的频控依赖客户端 IP 解析：只有部署在受信代理（cloud 网关 / 反向代理）之后，
 * `X-Forwarded-For` 才可信；boot 单体通常直接暴露端口，客户端可以伪造该头绕过 IP 限流，
 * 因此默认 **不信任** 代理头，直接用 `remoteAddr`。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "sms.rate-limit")
public class SmsRateLimitProperties {

	/**
	 * 是否信任 `X-Forwarded-For` / `X-Real-IP`。
	 * cloud 网关部署置 true（取 XFF 最后一跳，网关追加的真实客户端 IP）；boot 直连保持 false。
	 */
	private boolean trustForwardedHeaders = false;

}
