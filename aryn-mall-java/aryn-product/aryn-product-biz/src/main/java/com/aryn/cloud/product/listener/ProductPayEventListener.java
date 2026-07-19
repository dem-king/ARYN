
package com.aryn.cloud.product.listener;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.mapper.ProductOrderPayRecordMapper;
import com.aryn.cloud.product.service.IGoodsSpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付成功事件监听器-商品服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "product-service-pay-group")
public class ProductPayEventListener implements RocketMQListener<OrderPaySuccessEvent> {

	private final IGoodsSpuService goodsSpuService;

	private final ProductOrderPayRecordMapper productOrderPayRecordMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void onMessage(OrderPaySuccessEvent orderPaySuccessEvent) {
		ArynTenantContextHolder.removeTenantId();
		try {
			if (orderPaySuccessEvent == null || !StringUtils.hasText(orderPaySuccessEvent.getTenantId())
					|| !StringUtils.hasText(orderPaySuccessEvent.getOrderId())) {
				throw new IllegalArgumentException("支付成功消息缺少租户或订单标识");
			}
			ArynTenantContextHolder.setTenantId(orderPaySuccessEvent.getTenantId());
			log.info("支付成功事件监听器-商品服务收到消息：{}", orderPaySuccessEvent);
			if (CollectionUtils.isEmpty(orderPaySuccessEvent.getItemList())) {
				return;
			}
			Map<String, Integer> salesBySpu = new LinkedHashMap<>();
			orderPaySuccessEvent.getItemList().forEach(item -> {
				if (!StringUtils.hasText(item.getSpuId()) || item.getBuyQuantity() == null
						|| item.getBuyQuantity() <= 0) {
					throw new IllegalArgumentException("支付成功消息商品数据不合法");
				}
				salesBySpu.merge(item.getSpuId(), item.getBuyQuantity(), Integer::sum);
			});
			int inserted = productOrderPayRecordMapper.insertIfAbsent(IdUtil.getSnowflakeNextIdStr(),
				orderPaySuccessEvent.getOrderId(), orderPaySuccessEvent.getTenantId());
			if (inserted == 0) {
				log.info("商品销量消息已处理，幂等跳过, orderId={}", orderPaySuccessEvent.getOrderId());
				return;
			}
			for (Map.Entry<String, Integer> entry : salesBySpu.entrySet()) {
				if (!goodsSpuService.updateSalesVolume(entry.getKey(), entry.getValue())) {
					throw new IllegalStateException("商品销量更新失败, spuId=" + entry.getKey());
				}
			}
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
