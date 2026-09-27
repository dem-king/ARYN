package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.event.ArynOrderCreateBeforeEvent;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀预扣监听器行为契约：
 *   1. 赠品明细（giftFlag=1）不参与秒杀预扣——0 元赠品不应消耗秒杀限量名额；
 *   2. 命中进行中的秒杀时按 skuId/数量 预扣；
 *   3. 秒杀库存不足/超出限购时业务异常上抛，阻断整单。
 */
@ExtendWith(MockitoExtension.class)
class ArynOrderCreateBeforeEventListenerTest {

	@Mock
	private RemoteSeckillService remoteSeckillService;

	private ArynOrderCreateBeforeEventListener newListener() {
		return new ArynOrderCreateBeforeEventListener(remoteSeckillService);
	}

	private OrderInfo orderInfo() {
		return new OrderInfo().setId("order-1").setUserId("user-1");
	}

	private OrderItemEntity item(String skuId, String spuName, int quantity, String giftFlag) {
		return new OrderItemEntity()
				.setSkuId(skuId)
				.setSpuId("spu-1")
				.setSpuName(spuName)
				.setBuyQuantity(quantity)
				.setGiftFlag(giftFlag);
	}

	private AppSeckillGoodsVO seckillInfo(String skuId) {
		AppSeckillGoodsVO vo = new AppSeckillGoodsVO();
		vo.setId("sg-1");
		vo.setActivityId("act-1");
		vo.setSessionId("session-1");
		vo.setSkuId(skuId);
		vo.setSeckillPrice(new BigDecimal("9.90"));
		return vo;
	}

	@Test
	void giftItemsAreExcludedFromSeckillDeduction() {
		// 赠品 SKU 即使命中秒杀活动也不应预扣
		OrderItemEntity gift = item("sku-gift", "赠品", 1, "1");
		OrderItemEntity normal = item("sku-normal", "洗衣液", 2, "0");
		when(remoteSeckillService.getSeckillGoodsInfo("sku-normal")).thenReturn(null);

		newListener().seckillEventListener(
				new ArynOrderCreateBeforeEvent(this, orderInfo(), List.of(gift, normal)));

		verify(remoteSeckillService, never()).getSeckillGoodsInfo("sku-gift");
		verify(remoteSeckillService, never()).deductStock(any(), anyString(), anyString());
	}

	@Test
	void activeSeckillIsDeductedWithItemQuantity() {
		OrderItemEntity item = item("sku-1", "肉扒包", 3, "0");
		when(remoteSeckillService.getSeckillGoodsInfo("sku-1")).thenReturn(seckillInfo("sku-1"));
		when(remoteSeckillService.deductStock(any(), anyString(), anyString()))
				.thenReturn(new BigDecimal("29.70"));

		newListener().seckillEventListener(
				new ArynOrderCreateBeforeEvent(this, orderInfo(), List.of(item)));

		ArgumentCaptor<SeckillOrderDTO> captor = ArgumentCaptor.forClass(SeckillOrderDTO.class);
		verify(remoteSeckillService).deductStock(captor.capture(), anyString(), anyString());
		assertThat(captor.getValue().getSkuId()).isEqualTo("sku-1");
		assertThat(captor.getValue().getQuantity()).isEqualTo(3);
		assertThat(captor.getValue().getSessionId()).isEqualTo("session-1");
	}

	@Test
	void seckillFailureBlocksOrderCreation() {
		OrderItemEntity item = item("sku-1", "肉扒包", 3, "0");
		when(remoteSeckillService.getSeckillGoodsInfo("sku-1")).thenReturn(seckillInfo("sku-1"));
		when(remoteSeckillService.deductStock(any(), anyString(), anyString()))
				.thenThrow(new ArynBusinessException("手慢了，秒杀库存不足"));

		ArynOrderCreateBeforeEvent event = new ArynOrderCreateBeforeEvent(this, orderInfo(), List.of(item));
		assertThatThrownBy(() -> newListener().seckillEventListener(event))
				.isInstanceOf(ArynBusinessException.class)
				// ArynBusinessException 的文案在 getMsg() 字段（未走 RuntimeException.message）
				.extracting(ex -> ((ArynBusinessException) ex).getMsg())
				.asString()
				.contains("手慢了，秒杀库存不足");
	}

	@Test
	void seckillEndedSkipsDeduction() {
		OrderItemEntity item = item("sku-1", "肉扒包", 3, "0");
		when(remoteSeckillService.getSeckillGoodsInfo("sku-1")).thenReturn(null);

		newListener().seckillEventListener(
				new ArynOrderCreateBeforeEvent(this, orderInfo(), List.of(item)));

		verify(remoteSeckillService, never()).deductStock(any(), anyString(), anyString());
	}
}
