package com.aryn.cloud.order.support;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.order.api.entity.OrderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 订单超时取消延迟级别换算（纯函数）。
 *
 * <p>order_config.order_cancel_timeout 存的是 RocketMQ 延迟级别（字典 mq_delay_time_level），
 * 不是分钟数。三处消费方必须共用同一口径，否则会出现
 * 「MQ 按配置取消、兜底扫描按写死值取消、页面提示第三种」的漂移：
 * <ul>
 *   <li>下单后发延迟取消消息（ArynOrderCreateAfterEventListener）→ 用级别</li>
 *   <li>超时未支付兜底扫描（OrderJobHandler）→ 用换算后的分钟数</li>
 *   <li>C 端待付款提示（AppOrderInfoController）→ 用换算后的分钟数</li>
 * </ul>
 */
@Slf4j
public final class OrderCancelTimeoutHelper {

	private OrderCancelTimeoutHelper() {
	}

	/** 从订单配置解析延迟级别；未配置或非法时回落默认级别 */
	public static int resolveDelayLevel(OrderConfig orderConfig) {
		if (orderConfig == null || !StringUtils.hasText(orderConfig.getOrderCancelTimeout())) {
			return RocketMqConstants.ORDER_CANCEL_LEVEL;
		}
		try {
			return Integer.parseInt(orderConfig.getOrderCancelTimeout().trim());
		}
		catch (NumberFormatException exception) {
			log.warn("订单取消延迟等级配置不合法，使用默认值, value={}", orderConfig.getOrderCancelTimeout());
			return RocketMqConstants.ORDER_CANCEL_LEVEL;
		}
	}

	/**
	 * 延迟级别换算为分钟（RocketMQ 默认 messageDelayLevel：1s 5s 10s 30s 1m…10m 20m 30m 1h 2h）。
	 * 秒级（1~4）与未知级别统一回落默认级别的分钟数：
	 * 兜底扫描按此扣减 create_time，回落到 0 或负数会把未支付订单整批取消。
	 */
	public static int delayLevelToMinutes(int level) {
		return switch (level) {
			case 5, 6, 7, 8, 9, 10, 11, 12, 13, 14 -> level - 4;
			case 15 -> 20;
			case 16 -> 30;
			case 17 -> 60;
			case 18 -> 120;
			default -> delayLevelToMinutes(RocketMqConstants.ORDER_CANCEL_LEVEL);
		};
	}

}
