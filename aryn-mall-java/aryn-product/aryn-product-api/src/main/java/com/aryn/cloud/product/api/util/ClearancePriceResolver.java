package com.aryn.cloud.product.api.util;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 出清价口径（SPU 默认规则 + SKU 覆盖，NULL 继承）。
 *
 * <p>列表卡片、专区接口与结算同步必须共用同一份算法，否则会出现「卡片标 9.9、结算按 12.9」
 * 的价格口径漂移。这里只做纯计算，不查库、不感知租户与用户。
 *
 * @author aryn
 * @since 2026/11/03
 */
public final class ClearancePriceResolver {

	private ClearancePriceResolver() {
	}

	/** 固定价优先，否则原价 × 折扣率；都不可用时返回 null。 */
	public static BigDecimal resolvePrice(BigDecimal fixedPrice, BigDecimal originalPrice, BigDecimal rate) {
		if (fixedPrice != null && fixedPrice.signum() > 0) {
			return fixedPrice.setScale(2, RoundingMode.HALF_UP);
		}
		if (rate != null && rate.signum() > 0 && originalPrice != null && originalPrice.signum() > 0) {
			return originalPrice.multiply(rate).setScale(2, RoundingMode.HALF_UP);
		}
		return null;
	}

	/**
	 * 单个 SKU 的有效出清单价：未打标、已到期、倒挂（≥ 当前售价）一律返回 null。
	 */
	public static BigDecimal effectiveUnitPrice(GoodsSku sku, GoodsSpu spu, LocalDateTime now) {
		if (sku == null || spu == null || !isTagged(sku, spu)) {
			return null;
		}
		LocalDateTime expireTime = sku.getClearanceExpireTime() != null ? sku.getClearanceExpireTime()
				: spu.getClearanceExpireTime();
		if (expireTime != null && !expireTime.isAfter(now)) {
			return null;
		}
		BigDecimal salesPrice = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
		if (salesPrice == null || salesPrice.signum() <= 0) {
			return null;
		}
		BigDecimal price = resolvePrice(
				sku.getClearancePrice() != null ? sku.getClearancePrice() : spu.getClearancePrice(),
				sku.getOriginalPrice() != null ? sku.getOriginalPrice() : spu.getOriginalPrice(),
				sku.getClearanceDiscountRate() != null ? sku.getClearanceDiscountRate()
						: spu.getClearanceDiscountRate());
		if (price == null || price.signum() <= 0 || price.compareTo(salesPrice) >= 0) {
			return null;
		}
		return price;
	}

	/**
	 * SPU 卡片出清价（多规格取最低有效价）。
	 *
	 * <p>只统计「售价不高于 SPU 卡片价」的规格：卡片展示的是 SPU 聚合价，
	 * 贵规格的出清价不能拿来标低价规格的卡片。
	 */
	public static BigDecimal effectiveSpuPrice(GoodsSpu spu, List<GoodsSku> skus, LocalDateTime now) {
		if (spu == null) {
			return null;
		}
		BigDecimal best = null;
		LocalDateTime spuExpire = spu.getClearanceExpireTime();
		if (Objects.equals(spu.getIsClearanceItem(), 1)
				&& (spuExpire == null || spuExpire.isAfter(now))) {
			BigDecimal price = resolvePrice(spu.getClearancePrice(), spu.getOriginalPrice(),
					spu.getClearanceDiscountRate());
			if (price != null && spu.getSalesPrice() != null && price.compareTo(spu.getSalesPrice()) < 0) {
				best = price;
			}
		}
		if (skus == null) {
			return best;
		}
		for (GoodsSku sku : skus) {
			if (sku == null) {
				continue;
			}
			BigDecimal skuSales = sku.getSalesPrice() != null ? sku.getSalesPrice() : spu.getSalesPrice();
			if (spu.getSalesPrice() != null && skuSales != null && skuSales.compareTo(spu.getSalesPrice()) > 0) {
				continue;
			}
			BigDecimal price = effectiveUnitPrice(sku, spu, now);
			if (price != null && (best == null || price.compareTo(best) < 0)) {
				best = price;
			}
		}
		return best;
	}

	private static boolean isTagged(GoodsSku sku, GoodsSpu spu) {
		return sku.getIsClearanceItem() != null ? sku.getIsClearanceItem() == 1
				: Objects.equals(spu.getIsClearanceItem(), 1);
	}

}
