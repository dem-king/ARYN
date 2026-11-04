package com.aryn.cloud.product.api.util;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出清价口径：SPU 默认规则 + SKU 覆盖、到期与倒挂防护。
 */
class ClearancePriceResolverTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

	@Test
	@DisplayName("固定价优先，且必须低于当前售价")
	void fixedPriceWinsAndMustBeBelowSalesPrice() {
		GoodsSpu spu = spu("30.00", "100.00", "20.00", null, null);
		GoodsSku sku = sku("sku-1", "30.00", "100.00", null, null, null);

		assertThat(ClearancePriceResolver.effectiveUnitPrice(sku, spu, NOW)).isEqualByComparingTo("20.00");

		spu.setClearancePrice(new BigDecimal("30.00"));
		assertThat(ClearancePriceResolver.effectiveUnitPrice(sku, spu, NOW)).isNull();
	}

	@Test
	@DisplayName("未配置固定价时按原价 × 出清折扣")
	void rateBasedOnOriginalPrice() {
		GoodsSpu spu = spu("30.00", "100.00", null, "0.2500", null);
		GoodsSku sku = sku("sku-1", "30.00", "100.00", null, null, null);

		assertThat(ClearancePriceResolver.effectiveUnitPrice(sku, spu, NOW)).isEqualByComparingTo("25.00")
			.describedAs("原价 100 × 0.25 = 25");
	}

	@Test
	@DisplayName("已到期/未打标的 SKU 不产生出清价")
	void expiredOrUntaggedSkuIsSkipped() {
		GoodsSpu spu = spu("30.00", "100.00", "20.00", null, NOW.minusDays(1));
		GoodsSku sku = sku("sku-1", "30.00", "100.00", null, null, null);
		assertThat(ClearancePriceResolver.effectiveUnitPrice(sku, spu, NOW)).isNull();

		spu.setClearanceExpireTime(NOW.plusDays(1));
		spu.setIsClearanceItem(0);
		sku.setIsClearanceItem(0);
		assertThat(ClearancePriceResolver.effectiveUnitPrice(sku, spu, NOW)).isNull();
	}

	@Test
	@DisplayName("SKU 覆盖优先；SPU 卡片价取所有有效规格的最低价")
	void skuOverrideAndSpuCardPrice() {
		GoodsSpu spu = spu("30.00", "100.00", null, "0.2400", NOW.plusDays(1));
		GoodsSku cheap = sku("sku-cheap", "30.00", "100.00", "18.00", null, null);
		GoodsSku normal = sku("sku-normal", "30.00", "100.00", null, null, null);
		// 贵规格出清价更低也不能标到低价卡片上
		GoodsSku expensive = sku("sku-expensive", "50.00", "100.00", "5.00", null, null);

		assertThat(ClearancePriceResolver.effectiveUnitPrice(cheap, spu, NOW)).isEqualByComparingTo("18.00");
		assertThat(ClearancePriceResolver.effectiveUnitPrice(normal, spu, NOW)).isEqualByComparingTo("24.00");
		assertThat(ClearancePriceResolver.effectiveSpuPrice(spu, List.of(cheap, normal, expensive), NOW))
			.isEqualByComparingTo("18.00");
	}

	private GoodsSpu spu(String salesPrice, String originalPrice, String clearancePrice, String clearanceRate,
			LocalDateTime expireTime) {
		GoodsSpu spu = new GoodsSpu();
		spu.setIsClearanceItem(1);
		spu.setSalesPrice(new BigDecimal(salesPrice));
		spu.setOriginalPrice(new BigDecimal(originalPrice));
		spu.setClearancePrice(clearancePrice == null ? null : new BigDecimal(clearancePrice));
		spu.setClearanceDiscountRate(clearanceRate == null ? null : new BigDecimal(clearanceRate));
		spu.setClearanceExpireTime(expireTime);
		return spu;
	}

	private GoodsSku sku(String id, String salesPrice, String originalPrice, String clearancePrice,
			String clearanceRate, LocalDateTime expireTime) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		sku.setOriginalPrice(new BigDecimal(originalPrice));
		sku.setClearancePrice(clearancePrice == null ? null : new BigDecimal(clearancePrice));
		sku.setClearanceDiscountRate(clearanceRate == null ? null : new BigDecimal(clearanceRate));
		sku.setClearanceExpireTime(expireTime);
		return sku;
	}

}
