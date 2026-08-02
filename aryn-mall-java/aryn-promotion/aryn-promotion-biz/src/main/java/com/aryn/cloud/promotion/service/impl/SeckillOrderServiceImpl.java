package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SeckillOrderDTO;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.enums.SeckillOrderStatusEnum;
import com.aryn.cloud.promotion.mapper.SeckillOrderMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.promotion.service.SeckillStockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl extends ServiceImpl<SeckillOrderMapper, SeckillOrder>
		implements ISeckillOrderService {

	private final ISeckillGoodsService seckillGoodsService;

	private final ISeckillSessionService seckillSessionService;

	private final SeckillStockManager seckillStockManager;

	private final RedissonClient redissonClient;

	private final StringRedisTemplate redisTemplate;

	private static final String IDEMPOTENT_PREFIX = "seckill:idempotent:";

	private static final String LOCK_PREFIX = "seckill:lock:";

	@Override
	public BigDecimal createSeckillOrder(SeckillOrderDTO dto, String userId, String orderId) {
		// 幂等校验：同一用户+活动+场次 5分钟内防重提交
		String idempotentKey = IDEMPOTENT_PREFIX + userId + ":" + dto.getActivityId() + ":" + dto.getSessionId();
		Boolean first = redisTemplate.opsForValue().setIfAbsent(idempotentKey, orderId, Duration.ofMinutes(5));
		if (Boolean.FALSE.equals(first)) {
			log.warn("秒杀重复提交, userId={}, activityId={}, sessionId={}", userId, dto.getActivityId(), dto.getSessionId());
			throw new ArynBusinessException("请勿重复提交，请稍后再试");
		}

		// 分布式锁：同一 SKU 串行化（避免并发下单同一 SKU 导致 DB 不一致）
		String lockKey = LOCK_PREFIX + dto.getSkuId();
		RLock lock = redissonClient.getLock(lockKey);
		try {
			if (!lock.tryLock(3, 10, java.util.concurrent.TimeUnit.SECONDS)) {
				throw new ArynBusinessException("当前购买人数过多，请稍后再试");
			}

			// 校验场次进行中
			SeckillSession session = seckillSessionService.getById(dto.getSessionId());
			if (session == null || session.getStatus() == null || session.getStatus() != 1) {
				throw new ArynBusinessException("秒杀场次未开始或已结束");
			}
			LocalDateTime now = LocalDateTime.now();
			if (now.isBefore(session.getStartTime()) || now.isAfter(session.getEndTime())) {
				throw new ArynBusinessException("秒杀场次未开始或已结束");
			}

			// 查询秒杀商品
			SeckillGoods goods = seckillGoodsService.getById(dto.getSeckillGoodsId());
			if (goods == null) {
				throw new ArynBusinessException("秒杀商品不存在");
			}
			if (!goods.getSkuId().equals(dto.getSkuId())) {
				throw new ArynBusinessException("秒杀商品信息不匹配");
			}
			int limitPerUser = goods.getLimitPerUser() != null ? goods.getLimitPerUser() : 1;
			if (dto.getQuantity() > limitPerUser) {
				throw new ArynBusinessException("超过单人限购数量" + limitPerUser);
			}

			// Lua 原子扣减 Redis 库存
			long result = seckillStockManager.deductStock(
					dto.getActivityId(), dto.getSessionId(), dto.getSkuId(),
					userId, dto.getQuantity(), limitPerUser);
			if (result == 0) {
				throw new ArynBusinessException("手慢了，秒杀库存不足");
			}
			if (result == -1) {
				throw new ArynBusinessException("超过单人限购数量" + limitPerUser);
			}

			// 创建预扣记录
			try {
				SeckillOrder seckillOrder = new SeckillOrder();
				seckillOrder.setActivityId(dto.getActivityId());
				seckillOrder.setSessionId(dto.getSessionId());
				seckillOrder.setSeckillGoodsId(dto.getSeckillGoodsId());
				seckillOrder.setOrderId(orderId);
				seckillOrder.setUserId(userId);
				seckillOrder.setSkuId(dto.getSkuId());
				seckillOrder.setQuantity(dto.getQuantity());
				seckillOrder.setStatus(SeckillOrderStatusEnum.UNPAID.getCode());
				seckillOrder.setSeckillPrice(goods.getSeckillPrice());
				this.save(seckillOrder);
			} catch (Exception e) {
				// DB 写失败，回滚 Redis 库存
				seckillStockManager.rollbackStock(
						dto.getActivityId(), dto.getSessionId(), dto.getSkuId(),
						userId, dto.getQuantity());
				redisTemplate.delete(idempotentKey);
				log.error("秒杀订单创建失败，已回滚Redis库存, orderId={}", orderId, e);
				throw new ArynBusinessException("秒杀下单失败，请重试");
			}

			BigDecimal totalPrice = goods.getSeckillPrice().multiply(BigDecimal.valueOf(dto.getQuantity()));
			log.info("秒杀下单成功, orderId={}, userId={}, skuId={}, quantity={}, totalPrice={}",
					orderId, userId, dto.getSkuId(), dto.getQuantity(), totalPrice);
			return totalPrice;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ArynBusinessException("秒杀下单被中断");
		} finally {
			if (lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean rollbackByOrderId(String orderId) {
		SeckillOrder seckillOrder = this.getOne(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.last("LIMIT 1"));
		if (seckillOrder == null) {
			return false;
		}
		// 仅未支付状态可回滚
		if (!SeckillOrderStatusEnum.UNPAID.getCode().equals(seckillOrder.getStatus())) {
			log.warn("秒杀订单非未支付状态，跳过回滚, orderId={}, status={}", orderId, seckillOrder.getStatus());
			return false;
		}
		// CAS 更新状态为已取消
		boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.CANCELED.getCode()));
		if (!updated) {
			return false;
		}
		// 事务提交后回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				seckillStockManager.rollbackStock(
						seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
						seckillOrder.getUserId(), seckillOrder.getQuantity());
			}
		});
		log.info("秒杀订单取消回滚, orderId={}", orderId);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handlePaySuccess(String orderId) {
		SeckillOrder seckillOrder = this.getOne(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.last("LIMIT 1"));
		if (seckillOrder == null) {
			return;
		}
		// 幂等：仅未支付→已支付
		if (!SeckillOrderStatusEnum.UNPAID.getCode().equals(seckillOrder.getStatus())) {
			log.info("秒杀订单非未支付状态，跳过支付处理, orderId={}, status={}", orderId, seckillOrder.getStatus());
			return;
		}
		// CAS 状态更新
		boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.PAID.getCode()));
		if (!updated) {
			log.info("秒杀订单支付状态CAS失败(并发已处理), orderId={}", orderId);
			return;
		}
		// 更新已售数量（带条件防超卖）
		seckillGoodsService.update(Wrappers.<SeckillGoods>lambdaUpdate()
				.eq(SeckillGoods::getId, seckillOrder.getSeckillGoodsId())
				.apply("sold_count + {0} <= seckill_stock", seckillOrder.getQuantity())
				.setSql("sold_count = sold_count + " + seckillOrder.getQuantity()));
		log.info("秒杀支付成功处理, orderId={}, seckillGoodsId={}", orderId, seckillOrder.getSeckillGoodsId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleRefundSuccess(String orderId) {
		SeckillOrder seckillOrder = this.getOne(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.last("LIMIT 1"));
		if (seckillOrder == null) {
			return;
		}
		// 幂等：仅已支付→已取消
		if (!SeckillOrderStatusEnum.PAID.getCode().equals(seckillOrder.getStatus())) {
			log.info("秒杀订单非已支付状态，跳过退款处理, orderId={}, status={}", orderId, seckillOrder.getStatus());
			return;
		}
		// CAS 状态更新
		boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.PAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.CANCELED.getCode()));
		if (!updated) {
			log.info("秒杀订单退款状态CAS失败(并发已处理), orderId={}", orderId);
			return;
		}
		// 回滚 DB 已售数量（带条件防负数）
		seckillGoodsService.update(Wrappers.<SeckillGoods>lambdaUpdate()
				.eq(SeckillGoods::getId, seckillOrder.getSeckillGoodsId())
				.apply("sold_count >= {0}", seckillOrder.getQuantity())
				.setSql("sold_count = sold_count - " + seckillOrder.getQuantity()));
		// 事务提交后安全回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					seckillStockManager.rollbackStock(
							seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
							seckillOrder.getUserId(), seckillOrder.getQuantity());
				} catch (Exception e) {
					log.error("退款Redis库存回滚失败, orderId={}, skuId={}", orderId, seckillOrder.getSkuId(), e);
				}
			}
		});
		log.info("秒杀退款处理完成, orderId={}", orderId);
	}

	@Override
	public List<SeckillOrder> listExpiredUnpaid(int expireMinutes) {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(expireMinutes);
		return this.list(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.lt(SeckillOrder::getCreateTime, threshold));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean expireOrder(String orderId) {
		SeckillOrder seckillOrder = this.getOne(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.last("LIMIT 1"));
		if (seckillOrder == null) {
			return false;
		}
		if (!SeckillOrderStatusEnum.UNPAID.getCode().equals(seckillOrder.getStatus())) {
			return false;
		}
		// CAS 更新状态为已超时
		boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.EXPIRED.getCode()));
		if (!updated) {
			return false;
		}
		// 事务提交后回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					seckillStockManager.rollbackStock(
							seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
							seckillOrder.getUserId(), seckillOrder.getQuantity());
				} catch (Exception e) {
					log.error("超时订单Redis库存回滚失败, orderId={}", orderId, e);
				}
			}
		});
		log.info("秒杀订单超时处理, orderId={}", orderId);
		return true;
	}
}
