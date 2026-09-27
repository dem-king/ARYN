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
import java.util.ArrayList;
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

	private static final String RELEASE_PREFIX = "seckill:released:";

	private static final String LOCK_PREFIX = "seckill:lock:";

	@Override
	public BigDecimal createSeckillOrder(SeckillOrderDTO dto, String userId, String orderId) {
		// 幂等校验：同一订单+SKU 5分钟内防重提交。
		// 不可按「用户+活动+场次」判重：同一场次的多个秒杀商品会在同一订单内一起下单，
		// 按场次判重会把该订单的第二个秒杀商品误判为重复提交而阻断整单。
		String idempotentKey = IDEMPOTENT_PREFIX + orderId + ":" + dto.getSkuId();
		Boolean first = redisTemplate.opsForValue().setIfAbsent(idempotentKey, orderId, Duration.ofMinutes(5));
		if (Boolean.FALSE.equals(first)) {
			log.warn("秒杀重复提交, orderId={}, skuId={}", orderId, dto.getSkuId());
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

			// Redis 库存池缺失（被清空/重启、或活动由 SQL 直接导入）时按 DB 基线懒重建，
			// 否则 Lua 扣减对缺失 key 一律返回「库存不足」，整场秒杀都无法下单
			ensureStockPool(goods, session, limitPerUser);

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
		// 同一订单可有多个秒杀商品（多条预扣记录），必须全部回滚；
		// 历史实现只取 LIMIT 1，购物车同时买多个秒杀商品时只释放其中一件的库存。
		List<SeckillOrder> seckillOrders = this.list(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode()));
		if (seckillOrders.isEmpty()) {
			return false;
		}
		List<SeckillOrder> rollbackOrders = new ArrayList<>();
		for (SeckillOrder seckillOrder : seckillOrders) {
			// CAS 更新状态为已取消
			boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
					.eq(SeckillOrder::getId, seckillOrder.getId())
					.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
					.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.CANCELED.getCode()));
			if (updated) {
				rollbackOrders.add(seckillOrder);
			}
		}
		if (rollbackOrders.isEmpty()) {
			return false;
		}
		// 事务提交后回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (SeckillOrder seckillOrder : rollbackOrders) {
					seckillStockManager.rollbackStock(
							seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
							seckillOrder.getUserId(), seckillOrder.getQuantity());
				}
			}
		});
		log.info("秒杀订单取消回滚, orderId={}, 明细数={}", orderId, rollbackOrders.size());
		return true;
	}

	@Override
	public boolean releaseDeduct(SeckillOrderDTO dto, String userId, String orderId) {
		// 下单事务回滚时预扣记录可能已一并回滚，无法按订单号找回，故凭参数直接释放 Redis。
		// 用 NX 标记防止重复释放把库存越滚越多（同一 orderId+sku 只可能预扣一次）。
		String releaseKey = RELEASE_PREFIX + orderId + ":" + dto.getSkuId();
		Boolean firstRelease = redisTemplate.opsForValue()
			.setIfAbsent(releaseKey, "1", Duration.ofMinutes(5));
		if (Boolean.FALSE.equals(firstRelease)) {
			log.info("秒杀预扣已释放，跳过重复释放, orderId={}, skuId={}", orderId, dto.getSkuId());
			return false;
		}
		// 预扣记录若仍在（事务未回滚其插入），一并置为已取消，避免超时任务二次释放
		this.update(Wrappers.<SeckillOrder>lambdaUpdate()
			.eq(SeckillOrder::getOrderId, orderId)
			.eq(SeckillOrder::getSkuId, dto.getSkuId())
			.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
			.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.CANCELED.getCode()));
		long result = seckillStockManager.rollbackStock(
				dto.getActivityId(), dto.getSessionId(), dto.getSkuId(), userId, dto.getQuantity());
		log.info("秒杀预扣补偿释放, orderId={}, skuId={}, quantity={}, result={}",
				orderId, dto.getSkuId(), dto.getQuantity(), result);
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handlePaySuccess(String orderId) {
		List<SeckillOrder> seckillOrders = this.list(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode()));
		if (seckillOrders.isEmpty()) {
			return;
		}
		for (SeckillOrder seckillOrder : seckillOrders) {
			// 幂等：仅未支付→已支付
			boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.PAID.getCode()));
			if (!updated) {
				log.info("秒杀订单支付状态CAS失败(并发已处理), orderId={}, skuId={}", orderId, seckillOrder.getSkuId());
				continue;
			}
			// 更新已售数量（带条件防超卖）
			seckillGoodsService.update(Wrappers.<SeckillGoods>lambdaUpdate()
				.eq(SeckillGoods::getId, seckillOrder.getSeckillGoodsId())
				.apply("sold_count + {0} <= seckill_stock", seckillOrder.getQuantity())
				.setSql("sold_count = sold_count + " + seckillOrder.getQuantity()));
		}
		log.info("秒杀支付成功处理, orderId={}, 明细数={}", orderId, seckillOrders.size());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleRefundSuccess(String orderId) {
		List<SeckillOrder> seckillOrders = this.list(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.PAID.getCode()));
		if (seckillOrders.isEmpty()) {
			return;
		}
		List<SeckillOrder> refunded = new ArrayList<>();
		for (SeckillOrder seckillOrder : seckillOrders) {
			// 幂等：仅已支付→已取消
			boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.PAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.CANCELED.getCode()));
			if (!updated) {
				log.info("秒杀订单退款状态CAS失败(并发已处理), orderId={}, skuId={}", orderId, seckillOrder.getSkuId());
				continue;
			}
			refunded.add(seckillOrder);
			// 回滚 DB 已售数量（带条件防负数）
			seckillGoodsService.update(Wrappers.<SeckillGoods>lambdaUpdate()
				.eq(SeckillGoods::getId, seckillOrder.getSeckillGoodsId())
				.apply("sold_count >= {0}", seckillOrder.getQuantity())
				.setSql("sold_count = sold_count - " + seckillOrder.getQuantity()));
		}
		if (refunded.isEmpty()) {
			return;
		}
		// 事务提交后安全回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (SeckillOrder seckillOrder : refunded) {
					try {
						seckillStockManager.rollbackStock(
								seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
								seckillOrder.getUserId(), seckillOrder.getQuantity());
					} catch (Exception e) {
						log.error("退款Redis库存回滚失败, orderId={}, skuId={}", orderId, seckillOrder.getSkuId(), e);
					}
				}
			}
		});
		log.info("秒杀退款处理完成, orderId={}, 明细数={}", orderId, refunded.size());
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
		List<SeckillOrder> seckillOrders = this.list(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId)
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode()));
		if (seckillOrders.isEmpty()) {
			return false;
		}
		List<SeckillOrder> expired = new ArrayList<>();
		for (SeckillOrder seckillOrder : seckillOrders) {
			// CAS 更新状态为已超时
			boolean updated = this.update(Wrappers.<SeckillOrder>lambdaUpdate()
				.eq(SeckillOrder::getId, seckillOrder.getId())
				.eq(SeckillOrder::getStatus, SeckillOrderStatusEnum.UNPAID.getCode())
				.set(SeckillOrder::getStatus, SeckillOrderStatusEnum.EXPIRED.getCode()));
			if (updated) {
				expired.add(seckillOrder);
			}
		}
		if (expired.isEmpty()) {
			return false;
		}
		// 事务提交后回滚 Redis
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				for (SeckillOrder seckillOrder : expired) {
					try {
						seckillStockManager.rollbackStock(
								seckillOrder.getActivityId(), seckillOrder.getSessionId(), seckillOrder.getSkuId(),
								seckillOrder.getUserId(), seckillOrder.getQuantity());
					} catch (Exception e) {
						log.error("超时订单Redis库存回滚失败, orderId={}, skuId={}", orderId, seckillOrder.getSkuId(), e);
					}
				}
			}
		});
		log.info("秒杀订单超时处理, orderId={}, 明细数={}", orderId, expired.size());
		return true;
	}

	/**
	 * Redis 库存池缺失时按 DB 基线重建。
	 *
	 * <p>管理端创建活动会在事务提交后初始化库存池，但 Redis 被清空/重启、或活动由 SQL
	 * 直接导入时 key 可能缺失。重建基线取「秒杀总量 - DB 已售」；预扣中（未支付）的占用
	 * 只存在于 Redis，重建后无法恢复，属可接受的降级（Redis 丢失本身即不可恢复）。
	 * 单人限购计数同样无法重建，从零起算。
	 */
	private void ensureStockPool(SeckillGoods goods, SeckillSession session, int limitPerUser) {
		if (seckillStockManager.getRemainingStock(
				goods.getActivityId(), goods.getSessionId(), goods.getSkuId()) != null) {
			return;
		}
		int sold = goods.getSoldCount() == null ? 0 : goods.getSoldCount();
		int total = goods.getSeckillStock() == null ? 0 : goods.getSeckillStock();
		int remaining = Math.max(0, total - sold);
		Duration ttl = Duration.between(LocalDateTime.now(), session.getEndTime()).plusHours(1);
		if (ttl.isNegative() || ttl.isZero()) {
			ttl = Duration.ofHours(1);
		}
		seckillStockManager.ensureStockInitialized(
				goods.getActivityId(), goods.getSessionId(), goods.getSkuId(), remaining, limitPerUser, ttl);
	}
}
