
package com.aryn.cloud.product.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

/**
 * 支付成功事件监听器-商品服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "product-service-pay-group",
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class ProductPayEventListener implements RocketMQListener<OrderPaySuccessEvent> {

	private final IGoodsSpuService goodsSpuService;

	@Override
	public void onMessage(OrderPaySuccessEvent orderPaySuccessEvent) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
				"product-service-pay-group", orderPaySuccessEvent, () -> doConsume(orderPaySuccessEvent));
	}

	private void doConsume(OrderPaySuccessEvent orderPaySuccessEvent) {
		ArynTenantContextHolder.setTenantId(orderPaySuccessEvent.getTenantId());
		if (!CollectionUtils.isEmpty(orderPaySuccessEvent.getItemList())) {
			// 增加销量
			orderPaySuccessEvent.getItemList().forEach(item -> {
				goodsSpuService.updateSalesVolume(item.getSpuId(), item.getBuyQuantity());
			});
		}
	}

}
