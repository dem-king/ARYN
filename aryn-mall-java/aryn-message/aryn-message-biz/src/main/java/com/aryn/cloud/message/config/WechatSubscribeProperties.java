package com.aryn.cloud.message.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信订阅消息配置。
 * 在 application.yml 中配置：
 * <pre>
 * wechat:
 *   subscribe:
 *     app-id: wxXXXXXXXX
 *     template-map:
 *       ORDER_DELIVERY_TASK: <模板ID>
 *       ORDER_CREATE: <模板ID>
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.subscribe")
public class WechatSubscribeProperties {

	/** 小程序 AppID */
	private String appId;

	/** bizType → 微信模板ID 映射 */
	private Map<String, String> templateMap;
}