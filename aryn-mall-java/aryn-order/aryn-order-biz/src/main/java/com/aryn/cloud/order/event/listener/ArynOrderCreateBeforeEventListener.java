
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.CommonConstants;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单创建前事件监听器。
 *
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
	 * 秒杀活动订单：订单创建前预扣秒杀库存。
	 *
	 * <p>对每个订单项检查是否有进行中的秒杀活动，有则预扣 Redis 库存并校验单人限购；
	 * 库存不足/超出限购抛业务异常阻断整单（此时确认页展示的即是秒杀价，
	 * 若静默改按原价成交会造成价格不符，故宁可失败并提示用户）。
	 *
	 * <p>预扣发生在下单事务内，事务回滚会连预扣记录一起回滚，仅靠定时任务无法释放 Redis，
	 * 因此本监听器自行登记事务回滚补偿：凡已扣减的项在回滚时按参数直接释放。
	 */
	@EventListener(ArynOrderCreateBeforeEvent.class)
	public void seckillEventListener(ArynOrderCreateBeforeEvent event) {
		OrderInfo orderInfo = event.getOrderInfo();
		List<OrderItemEntity> orderItemEntityList = event.getOrderItemEntityList();
		String userId = orderInfo.getUserId();
		String orderId = orderInfo.getId();
		List<SeckillOrderDTO> deductedList = new ArrayList<>();
		registerRollbackCompensation(deductedList, userId, orderId);

		for (OrderItemEntity item : orderItemEntityList) {
			// 赠品为 0 元追加明细，不参与秒杀预扣，避免误扣限量库存
			if (CommonConstants.YES.equals(item.getGiftFlag())) {
				continue;
			}
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
					throw new ArynBusinessException("秒杀商品「" + item.getSpuName() + "」已抢完，请移除后重试");
				}
				// 先登记再继续：本项之后若失败，补偿需包含本项
				deductedList.add(dto);
				log.info("秒杀预扣成功, orderId={}, skuId={}, spuName={}, quantity={}, seckillPrice={}",
						orderId, item.getSkuId(), item.getSpuName(), item.getBuyQuantity(), result);
			}
			catch (ArynBusinessException e) {
				throw e;
			}
			catch (Exception e) {
				log.warn("秒杀预扣检查异常(降级放行), orderId={}, skuId={}", orderId, item.getSkuId(), e);
			}
		}
	}

	/**
	 * 事务回滚补偿：释放已预扣的 Redis 秒杀库存。
	 *
	 * <p>必须在预扣前登记，且以可变列表累积——循环中途抛异常时后续代码不会执行，
	 * 只有提前登记的补偿才能覆盖「第 1 件已扣、第 2 件失败」的中间态。
	 */
	private void registerRollbackCompensation(List<SeckillOrderDTO> deductedList, String userId, String orderId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// 非事务上下文中失败不会回滚预扣记录，交由超时任务释放
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == TransactionSynchronization.STATUS_COMMITTED || deductedList.isEmpty()) {
					return;
				}
				for (SeckillOrderDTO deducted : deductedList) {
					try {
						remoteSeckillService.releaseDeduct(deducted, userId, orderId);
					}
					catch (Exception e) {
						log.error("秒杀预扣回滚释放失败, orderId={}, skuId={}", orderId, deducted.getSkuId(), e);
					}
				}
			}
		});
	}
}
