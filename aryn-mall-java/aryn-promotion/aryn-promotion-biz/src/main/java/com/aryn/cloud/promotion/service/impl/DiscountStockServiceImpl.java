package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 折扣商品库存占用实现。
 *
 * <p>占用顺序：先写占用明细（唯一键挡重复/并发）→ 再原子扣减 sold_count；
 * 扣减失败抛回滚，明细一并撤销。释放顺序相反但要先回补、再置明细为已释放。
 * 租户全部显式传入与过滤：Dubbo 提供方线程上的租户上下文不可靠。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountStockServiceImpl implements DiscountStockService {

	private final DiscountGoodsMapper discountGoodsMapper;

	private final DiscountStockLockMapper discountStockLockMapper;

	private final DiscountActivityMapper discountActivityMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public DiscountStockReserveResultDTO reserve(String tenantId, String orderId,
			List<DiscountStockItemDTO> items) {
		if (!StringUtils.hasText(orderId) || CollectionUtils.isEmpty(items)) {
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
			DiscountGoods goods = findActiveGoods(tenantId, item.getSkuId());
			// 无进行中的折扣 / 未配置限量：不占用，行为与改造前一致
			if (goods == null || goods.getStock() == null) {
				continue;
			}
			DiscountStockLock lock = new DiscountStockLock()
				.setTenantId(tenantId)
				.setActivityId(goods.getActivityId())
				.setSkuId(item.getSkuId())
				.setOrderId(orderId)
				.setQuantity(quantity)
				.setStatus(DiscountStockLock.STATUS_LOCKED);
			try {
				discountStockLockMapper.insert(lock);
			} catch (DuplicateKeyException duplicate) {
				// 并发下另一个线程刚写入同一 (订单, SKU)：本次不再扣减
				log.info("折扣库存占用并发命中唯一键，幂等跳过, orderId={}, skuId={}", orderId, item.getSkuId());
				continue;
			}
			if (discountGoodsMapper.tryReserveStock(goods.getId(), quantity) == 0) {
				// 抛出让整个事务回滚（含刚写入的占用明细）
				throw new ArynBusinessException("折扣商品库存不足，请调整数量后重试");
			}
		}
		return DiscountStockReserveResultDTO.ok();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int release(String tenantId, String orderId, String reason) {
		if (!StringUtils.hasText(orderId)) {
			return 0;
		}
		List<DiscountStockLock> locks = discountStockLockMapper.selectList(
				Wrappers.<DiscountStockLock>lambdaQuery()
					.eq(DiscountStockLock::getTenantId, tenantId)
					.eq(DiscountStockLock::getOrderId, orderId)
					.eq(DiscountStockLock::getStatus, DiscountStockLock.STATUS_LOCKED));
		if (CollectionUtils.isEmpty(locks)) {
			return 0;
		}
		for (DiscountStockLock lock : locks) {
			DiscountGoods goods = discountGoodsMapper.selectOne(Wrappers.<DiscountGoods>lambdaQuery()
					.eq(DiscountGoods::getTenantId, tenantId)
					.eq(DiscountGoods::getActivityId, lock.getActivityId())
					.eq(DiscountGoods::getSkuId, lock.getSkuId())
					.last("LIMIT 1"));
			if (goods == null || goods.getStock() == null) {
				continue;
			}
			discountGoodsMapper.restoreStock(goods.getId(), lock.getQuantity() == null ? 0 : lock.getQuantity());
		}
		int released = discountStockLockMapper.releaseByOrder(tenantId, orderId,
				StringUtils.hasText(reason) ? reason : "cancel");
		log.info("折扣库存释放完成, orderId={}, rows={}, reason={}", orderId, released, reason);
		return released;
	}

	/**
	 * 找出该 SKU 当前进行中的折扣商品行（含总限量配置）。
	 * 同一 SKU 可能挂在多个历史活动上，只认「活动进行中 + 商品行存在」的那一条。
	 */
	private DiscountGoods findActiveGoods(String tenantId, String skuId) {
		List<DiscountGoods> rows = discountGoodsMapper.selectList(Wrappers.<DiscountGoods>lambdaQuery()
				.eq(DiscountGoods::getTenantId, tenantId)
				.eq(DiscountGoods::getSkuId, skuId));
		if (CollectionUtils.isEmpty(rows)) {
			return null;
		}
		List<String> activityIds = rows.stream()
			.map(DiscountGoods::getActivityId)
			.distinct()
			.collect(Collectors.toList());
		LocalDateTime now = LocalDateTime.now();
		List<DiscountActivity> activities = discountActivityMapper.selectList(
				Wrappers.<DiscountActivity>lambdaQuery()
					.in(DiscountActivity::getId, activityIds)
					.eq(DiscountActivity::getTenantId, tenantId)
					.eq(DiscountActivity::getStatus, 1)
					.le(DiscountActivity::getStartTime, now)
					.ge(DiscountActivity::getEndTime, now));
		if (CollectionUtils.isEmpty(activities)) {
			return null;
		}
		Set<String> activeIds = activities.stream().map(DiscountActivity::getId).collect(Collectors.toSet());
		return rows.stream()
			.filter(row -> activeIds.contains(row.getActivityId()))
			.findFirst()
			.orElse(null);
	}

}