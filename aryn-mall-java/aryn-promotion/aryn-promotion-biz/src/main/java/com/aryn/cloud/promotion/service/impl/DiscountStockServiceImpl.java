package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.dto.DiscountStockReserveResultDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.entity.DiscountStockLock;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.mapper.DiscountGoodsMapper;
import com.aryn.cloud.promotion.mapper.DiscountStockLockMapper;
import com.aryn.cloud.promotion.service.DiscountStockService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 折扣库存占用实现（商品级 + 全场活动级）。
 *
 * <p>额度归属按「实际生效的活动」判断：指定商品活动（scope=2）扣 discount_goods 行，
 * 全场活动（scope=1）扣 discount_activity 行——后者没有商品行，额度只能挂在活动上。
 * 一个 SKU 可能同时被多个活动覆盖（全场 + 指定商品，取最优价），因此必须先用与
 * {@code getGoodsDiscountInfo} 相同的规则选出真正生效的那一个，否则会扣错活动。
 *
 * <p>占用顺序：先写占用明细（唯一键挡重复/并发）→ 再原子扣减；扣减失败抛回滚，
 * 明细一并撤销。租户全部显式传入与过滤：Dubbo 提供方线程上的租户上下文不可靠。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountStockServiceImpl implements DiscountStockService {

	private final DiscountActivityMapper discountActivityMapper;

	private final DiscountGoodsMapper discountGoodsMapper;

	private final DiscountStockLockMapper discountStockLockMapper;

	/**
	 * 商品域：选「最优活动」需要与定价同源的销售价，取不到时按未享受折扣处理（不占用）。
	 */
	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public DiscountStockReserveResultDTO reserve(String tenantId, String orderId,
			List<DiscountStockItemDTO> items) {
		if (!StringUtils.hasText(orderId) || CollectionUtils.isEmpty(items)) {
			return DiscountStockReserveResultDTO.ok();
		}
		if (!StringUtils.hasText(tenantId)) {
			// 租户为空时无法定位商品行：记录告警并按不限量放行，避免带 null 条件做无意义查询
			log.warn("折扣库存占用缺少租户，跳过, orderId={}", orderId);
			return DiscountStockReserveResultDTO.ok();
		}
		for (DiscountStockItemDTO item : items) {
			if (item == null || !StringUtils.hasText(item.getSkuId())
					|| item.getQuantity() == null || item.getQuantity() <= 0) {
				continue;
			}
			int quantity = item.getQuantity();
			// 幂等：同一订单同一 SKU 只占用一次（Dubbo 重试/重复提交）
			Long locked = discountStockLockMapper.selectCount(Wrappers.<DiscountStockLock>lambdaQuery()
					.eq(DiscountStockLock::getTenantId, tenantId)
					.eq(DiscountStockLock::getOrderId, orderId)
					.eq(DiscountStockLock::getSkuId, item.getSkuId()));
			if (locked != null && locked > 0) {
				continue;
			}
			ActiveDiscount active = resolveActiveDiscount(tenantId, item.getSkuId());
			// 无进行中的折扣 / 未配置限量：不占用，行为与改造前一致
			if (active == null || active.stock() == null) {
				continue;
			}
			DiscountStockLock lock = new DiscountStockLock()
				.setTenantId(tenantId)
				.setActivityId(active.activity().getId())
				.setSkuId(item.getSkuId())
				.setOrderId(orderId)
				.setOrderItemId(item.getOrderItemId())
				.setQuantity(quantity)
				.setStatus(DiscountStockLock.STATUS_LOCKED);
			try {
				discountStockLockMapper.insert(lock);
			} catch (DuplicateKeyException duplicate) {
				// 并发下另一个线程刚写入同一 (订单, SKU)：本次不再扣减
				log.info("折扣库存占用并发命中唯一键，幂等跳过, orderId={}, skuId={}", orderId, item.getSkuId());
				continue;
			}
			int updated = active.isFullScope()
					? discountActivityMapper.tryReserveStock(active.activity().getId(), quantity)
					: discountGoodsMapper.tryReserveStock(active.goods().getId(), quantity);
			if (updated == 0) {
				// 抛出让整个事务回滚（含刚写入的占用明细）
				throw new ArynBusinessException("折扣商品库存不足，请调整数量后重试");
			}
		}
		return DiscountStockReserveResultDTO.ok();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int release(String tenantId, String orderId, String orderItemId, String reason) {
		if (!StringUtils.hasText(orderId)) {
			return 0;
		}
		// 部分退款只释放该明细：同订单其它商品的占用必须保留，否则会超卖
		boolean partial = StringUtils.hasText(orderItemId);
		List<DiscountStockLock> locks = discountStockLockMapper.selectList(
				Wrappers.<DiscountStockLock>lambdaQuery()
					.eq(DiscountStockLock::getTenantId, tenantId)
					.eq(DiscountStockLock::getOrderId, orderId)
					.eq(partial, DiscountStockLock::getOrderItemId, orderItemId)
					.eq(DiscountStockLock::getStatus, DiscountStockLock.STATUS_LOCKED));
		if (CollectionUtils.isEmpty(locks)) {
			return 0;
		}
		for (DiscountStockLock lock : locks) {
			restoreLock(tenantId, lock);
		}
		int released = partial
				? discountStockLockMapper.releaseByOrderItem(tenantId, orderId, orderItemId, reasonOrDefault(reason))
				: discountStockLockMapper.releaseByOrder(tenantId, orderId, reasonOrDefault(reason));
		log.info("折扣库存释放完成, orderId={}, orderItemId={}, rows={}, reason={}",
				orderId, orderItemId, released, reason);
		return released;
	}

	/**
	 * 回补一条占用：按占用时归属的活动形态决定退回活动额度还是商品额度。
	 * 活动被删/改形态时退回商品行（若还存在），保证不漏退。
	 */
	private void restoreLock(String tenantId, DiscountStockLock lock) {
		int quantity = lock.getQuantity() == null ? 0 : lock.getQuantity();
		if (quantity <= 0) {
			return;
		}
		DiscountActivity activity = discountActivityMapper.selectById(lock.getActivityId());
		if (activity != null && activity.getScope() != null && activity.getScope() == 1) {
			discountActivityMapper.restoreStock(activity.getId(), quantity);
			return;
		}
		DiscountGoods goods = discountGoodsMapper.selectOne(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getTenantId, tenantId)
				.eq(DiscountGoods::getActivityId, lock.getActivityId())
				.eq(DiscountGoods::getSkuId, lock.getSkuId())
				.last("LIMIT 1"));
		if (goods != null && goods.getStock() != null) {
			discountGoodsMapper.restoreStock(goods.getId(), quantity);
		}
	}

	/**
	 * 选出该 SKU 当前**实际生效**的折扣活动：与 {@code getGoodsDiscountInfo} 同规则
	 * （进行中 + 命中范围 + 最低价），保证占用与定价指向同一个活动。
	 * 取不到商品售价时按「未享受折扣」处理，不占用额度。
	 */
	private ActiveDiscount resolveActiveDiscount(String tenantId, String skuId) {
		List<DiscountActivity> activities = discountActivityMapper.selectList(
				Wrappers.<DiscountActivity>lambdaQuery()
					.eq(DiscountActivity::getTenantId, tenantId)
					.eq(DiscountActivity::getStatus, 1)
					.le(DiscountActivity::getStartTime, LocalDateTime.now())
					.gt(DiscountActivity::getEndTime, LocalDateTime.now()));
		if (CollectionUtils.isEmpty(activities)) {
			return null;
		}
		BigDecimal originalPrice = loadSalesPrice(skuId);
		if (originalPrice == null) {
			return null;
		}
		ActiveDiscount best = null;
		BigDecimal bestPrice = originalPrice;
		for (DiscountActivity activity : activities) {
			DiscountGoods goods = null;
			if (activity.getScope() == null || activity.getScope() != 1) {
				goods = discountGoodsMapper.selectOne(Wrappers.<DiscountGoods>lambdaQuery()
						.eq(DiscountGoods::getTenantId, tenantId)
						.eq(DiscountGoods::getActivityId, activity.getId())
						.eq(DiscountGoods::getSkuId, skuId)
						.last("LIMIT 1"));
				if (goods == null) {
					continue;
				}
			}
			BigDecimal price = DiscountActivityServiceImpl.calculateDiscountPrice(
					originalPrice, activity.getDiscountType(), activity.getDiscountValue());
			if (price.compareTo(bestPrice) < 0) {
				bestPrice = price;
				best = new ActiveDiscount(activity, goods);
			}
		}
		return best;
	}

	private BigDecimal loadSalesPrice(String skuId) {
		try {
			List<GoodsSku> skus = remoteGoodsSkuService.getBySkuIds(List.of(skuId));
			if (CollectionUtils.isEmpty(skus) || skus.get(0).getSalesPrice() == null) {
				return null;
			}
			return skus.get(0).getSalesPrice();
		} catch (Exception e) {
			log.warn("查询 SKU 售价失败，跳过折扣库存占用, skuId={}", skuId, e);
			return null;
		}
	}

	private String reasonOrDefault(String reason) {
		return StringUtils.hasText(reason) ? reason : "cancel";
	}

	/**
	 * 生效中的折扣：全场型没有 goods 行（额度在活动上），指定商品型两者都有。
	 */
	private record ActiveDiscount(DiscountActivity activity, DiscountGoods goods) {

		boolean isFullScope() {
			return goods == null;
		}

		Integer stock() {
			return isFullScope() ? activity.getStock() : goods.getStock();
		}
	}

}
