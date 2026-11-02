package com.aryn.cloud.promotion.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀开抢提醒配置。
 * <p>template-id 对应微信公众平台的订阅消息模板，需与 aryn-message 的
 * {@code wechat.subscribe.template-map.SECKILL_SESSION_START} 保持一致；
 * 为空时 C 端隐藏提醒入口，Job 只落库不发送。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aryn.seckill.reminder")
public class SeckillReminderProperties {

	/** 是否启用开抢提醒 */
	private boolean enabled = true;

	/** 微信订阅模板ID */
	private String templateId;

	/** 提前多少分钟提醒 */
	private int offsetMinutes = 10;
}
