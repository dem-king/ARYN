
package com.aryn.cloud.product.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.service.IGoodsSkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

	@Override
	public void onMessage(OrderRefundSuccessEvent orderRefundSuccessEvent) {
		ArynTenantContextHolder.setTenantId(orderRefundSuccessEvent.getTenantId());
		log.info("退款成功事件监听器-商品服务务收到消息：{}", orderRefundSuccessEvent);
		OrderItemRefundSuccessEvent orderItem = orderRefundSuccessEvent.getOrderItem();
		GoodsSkuStockReqDTO goodsSkuStockRqDTO = new GoodsSkuStockReqDTO();
		goodsSkuStockRqDTO.setStockNum(orderItem.getBuyQuantity());
		goodsSkuStockRqDTO.setSkuId(orderItem.getSkuId());
		goodsSkuStockRqDTO.setSpuId(orderItem.getSpuId());
		List<GoodsSkuStockReqDTO> goodsSkuStockRqDTOList = new ArrayList<>();
		goodsSkuStockRqDTOList.add(goodsSkuStockRqDTO);
		goodsSkuService.rollbackStockList(goodsSkuStockRqDTOList);
	}

}
