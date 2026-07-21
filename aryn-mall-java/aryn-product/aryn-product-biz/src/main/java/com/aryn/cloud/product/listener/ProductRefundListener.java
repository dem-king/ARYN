
package com.aryn.cloud.product.listener;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.mapper.ProductRefundStockRecordMapper;
import com.aryn.cloud.product.service.IGoodsSkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 退款成功事件监听器-商品服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "product-service-refund-group")
public class ProductRefundListener implements RocketMQListener<OrderRefundSuccessEvent> {

	private final IGoodsSkuService goodsSkuService;

	private final ProductRefundStockRecordMapper productRefundStockRecordMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void onMessage(OrderRefundSuccessEvent orderRefundSuccessEvent) {
		ArynTenantContextHolder.removeTenantId();
		try {
			validateEvent(orderRefundSuccessEvent);
			ArynTenantContextHolder.setTenantId(orderRefundSuccessEvent.getTenantId());
			log.info("退款成功事件监听器-商品服务收到消息：{}", orderRefundSuccessEvent);
			if (productRefundStockRecordMapper.insertIfAbsent(IdUtil.getSnowflakeNextIdStr(),
					orderRefundSuccessEvent.getRefundNo(), orderRefundSuccessEvent.getTenantId()) == 0) {
				log.info("退款库存已恢复，幂等跳过, refundNo={}", orderRefundSuccessEvent.getRefundNo());
				return;
			}
			OrderItemRefundSuccessEvent orderItem = orderRefundSuccessEvent.getOrderItem();
			GoodsSkuStockReqDTO stockRequest = new GoodsSkuStockReqDTO();
			stockRequest.setStockNum(orderItem.getBuyQuantity());
			stockRequest.setSkuId(orderItem.getSkuId());
			stockRequest.setSpuId(orderItem.getSpuId());
			goodsSkuService.rollbackStockList(List.of(stockRequest));
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private void validateEvent(OrderRefundSuccessEvent event) {
		if (event == null || !StringUtils.hasText(event.getTenantId()) || !StringUtils.hasText(event.getRefundNo())) {
			throw new IllegalArgumentException("退款成功消息缺少租户或退款业务号");
		}
		OrderItemRefundSuccessEvent orderItem = event.getOrderItem();
		if (orderItem == null || !StringUtils.hasText(orderItem.getSkuId())
				|| !StringUtils.hasText(orderItem.getSpuId()) || orderItem.getBuyQuantity() == null
				|| orderItem.getBuyQuantity() <= 0) {
			throw new IllegalArgumentException("退款成功消息商品数据不合法");
		}
	}

}
