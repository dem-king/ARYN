package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.remote.RemoteSeckillService;
import com.aryn.cloud.promotion.api.vo.AppSeckillGoodsVO;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 秒杀 Dubbo 远程服务实现
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSeckillServiceImpl implements RemoteSeckillService {

	private final ISeckillOrderService seckillOrderService;

	private final ISeckillActivityService seckillActivityService;

	@Override
	public BigDecimal deductStock(SeckillOrderDTO dto, String userId, String orderId) {
		return seckillOrderService.createSeckillOrder(dto, userId, orderId);
	}

	@Override
	public boolean rollbackStock(String orderId) {
		return seckillOrderService.rollbackByOrderId(orderId);
	}

	@Override
	public boolean releaseDeduct(SeckillOrderDTO dto, String userId, String orderId) {
		try {
			return seckillOrderService.releaseDeduct(dto, userId, orderId);
		}
		catch (Exception e) {
			log.error("秒杀预扣补偿释放失败, orderId={}, skuId={}", orderId, dto.getSkuId(), e);
			return false;
		}
	}

	@Override
	public BigDecimal getSeckillPrice(String skuId) {
		AppSeckillGoodsVO vo = seckillActivityService.getGoodsSeckillInfo(skuId);
		if (vo == null || vo.getSeckillPrice() == null) {
			return null;
		}
		return vo.getSeckillPrice();
	}

	@Override
	public AppSeckillGoodsVO getSeckillGoodsInfo(String skuId) {
		return seckillActivityService.getGoodsSeckillInfo(skuId);
	}
}