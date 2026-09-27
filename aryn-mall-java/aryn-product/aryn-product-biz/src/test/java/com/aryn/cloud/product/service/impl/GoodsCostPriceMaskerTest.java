
package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.util.GoodsCostPriceMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 成本价脱敏的 JSON 序列化契约。
 *
 * <p>两条口径都必须锁住：
 * <ul>
 * <li>C 端出参经 {@code GoodsCostPriceMasker} 置空后，配合实体字段上的
 * {@code @JsonInclude(NON_NULL)}，{@code costPrice} 键从 JSON 中整个消失
 * ——而不是留一个 {@code "costPrice": null}；</li>
 * <li>管理端出参（成本价有值）序列化不受注解影响，表单回显依赖它。</li>
 * </ul>
 */
class GoodsCostPriceMaskerTest {

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void maskedSpuOmitsCostPriceKeyFromJson() throws Exception {
		GoodsSpu spu = spu();
		GoodsCostPriceMasker.maskSpu(spu);

		String json = objectMapper.writeValueAsString(spu);

		assertThat(json).doesNotContain("costPrice");
		assertThat(json).contains("\"salesPrice\":20");
		assertThat(json).contains("\"originalPrice\":80");
	}

	@Test
	void maskedSpuAlsoMasksNestedSkus() throws Exception {
		GoodsSpu spu = spu();
		GoodsSku sku = sku();
		spu.setGoodsSkus(List.of(sku));
		GoodsCostPriceMasker.maskSpu(spu);

		String json = objectMapper.writeValueAsString(spu);

		assertThat(json).doesNotContain("costPrice");
	}

	@Test
	void spuWithCostPriceKeepsTheKeyForAdminEndpoints() throws Exception {
		// 管理端出参成本价有值：注解只在为 null 时省略键，有值时照常序列化
		String json = objectMapper.writeValueAsString(spu());

		assertThat(json).contains("\"costPrice\":40.05");
	}

	@Test
	void maskedSkuOmitsCostPriceKeyFromJson() throws Exception {
		GoodsSku sku = sku();
		GoodsCostPriceMasker.maskSku(sku);

		String json = objectMapper.writeValueAsString(sku);

		assertThat(json).doesNotContain("costPrice");
		assertThat(json).contains("\"salesPrice\":20");
	}

	@Test
	void skuWithCostPriceKeepsTheKeyForAdminEndpoints() throws Exception {
		String json = objectMapper.writeValueAsString(sku());

		assertThat(json).contains("\"costPrice\":6.5");
	}

	@Test
	void maskerToleratesNullInputs() {
		assertThat(GoodsCostPriceMasker.maskSpu(null)).isNull();
		assertThat(GoodsCostPriceMasker.maskSku(null)).isNull();
	}

	private GoodsSpu spu() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setSalesPrice(BigDecimal.valueOf(20));
		spu.setOriginalPrice(BigDecimal.valueOf(80));
		spu.setCostPrice(BigDecimal.valueOf(40.05));
		return spu;
	}

	private GoodsSku sku() {
		GoodsSku sku = new GoodsSku();
		sku.setId("sku-1");
		sku.setSalesPrice(BigDecimal.valueOf(20));
		sku.setCostPrice(BigDecimal.valueOf(6.5));
		return sku;
	}

}
