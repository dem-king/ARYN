
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.event.ArynOrderCreateBeforeEvent;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderCreateBeforeEventListener {

	@DubboReference
	private final RemoteSeckillService remoteSeckillService;

	/**
	 * TODO 拼团活动订单
	 */
	@EventListener(ArynOrderCreateBeforeEvent.class)
	public void groupEventListener(ArynOrderCreateBeforeEvent event) {

	}

	/**
	 * 秒杀活动订单：订单创建前预扣秒杀库存
	 * 对每个订单项检查是否有进行中的秒杀活动，有则预扣 Redis 库存
	 * 预扣失败（库存不足/超出限购）则抛异常阻止订单创建
	 */
	@EventListener(ArynOrderCreateBeforeEvent.class)
	public void seckillEventListener(ArynOrderCreateBeforeEvent event) {
		OrderInfo orderInfo = event.getOrderInfo();
		List<OrderItemEntity> orderItemEntityList = event.getOrderItemEntityList();
		String userId = orderInfo.getUserId();
		String orderId = orderInfo.getId();
		for (OrderItemEntity item : orderItemEntityList) {
			try {
				AppSeckillGoodsVO seckillInfo = remoteSeckillService.getSeckillGoodsInfo(item.getSkuId());
				if (seckillInfo == null) {
					continue;
				}
				SeckillOrderDTO dto = new SeckillOrderDTO();
				dto.setActivityId(seckillInfo.getActivityId());
				dto.setSessionId(seckillInfo.getSessionId());
				dto.setSeckillGoodsId(seckillInfo.getId());
				dto.setSkuId(item.getSkuId());
				dto.setQuantity(item.getBuyQuantity());
				BigDecimal result = remoteSeckillService.deductStock(dto, userId, orderId);
				if (result == null) {
					throw new ArynBusinessException("秒杀库存不足或超出限购");
				}
				log.info("秒杀预扣成功, orderId={}, skuId={}, quantity={}, seckillPrice={}",
						orderId, item.getSkuId(), item.getBuyQuantity(), result);
			} catch (ArynBusinessException e) {
				throw e;
			} catch (Exception e) {
				log.warn("秒杀预扣检查异常(降级放行), orderId={}, skuId={}", orderId, item.getSkuId(), e);
			}
		}
	}
}
