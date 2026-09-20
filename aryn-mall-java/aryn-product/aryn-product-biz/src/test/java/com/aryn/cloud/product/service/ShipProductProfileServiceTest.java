package com.aryn.cloud.product.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ShipProductProfileDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ShipGoodsProfile;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.ShipProductSummaryVO;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.ProductCodeMappingMapper;
import com.aryn.cloud.product.mapper.ShipGoodsProfileMapper;
import com.aryn.cloud.product.mapper.ShipSkuProfileMapper;
import com.aryn.cloud.product.service.impl.ShipProductProfileServiceImpl;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 船供商品资料服务契约测试。
 */
class ShipProductProfileServiceTest {

	private static final String TENANT = "tenant-1";

	private static final String SPU_ID = "spu-1";

	private ShipGoodsProfileMapper shipGoodsProfileMapper;

	private ShipSkuProfileMapper shipSkuProfileMapper;

	private ProductCodeMappingMapper productCodeMappingMapper;

	private GoodsSpuMapper goodsSpuMapper;

	private ShipProductProfileServiceImpl service;

	@BeforeEach
	void setUp() {
		shipGoodsProfileMapper = mock(ShipGoodsProfileMapper.class);
		shipSkuProfileMapper = mock(ShipSkuProfileMapper.class);
		productCodeMappingMapper = mock(ProductCodeMappingMapper.class);
		goodsSpuMapper = mock(GoodsSpuMapper.class);
		service = new ShipProductProfileServiceImpl(shipGoodsProfileMapper, shipSkuProfileMapper,
				productCodeMappingMapper, goodsSpuMapper);

		GoodsSpu spu = new GoodsSpu();
		spu.setId(SPU_ID);
		spu.setTenantId(TENANT);
		spu.setName("测试商品");
		when(goodsSpuMapper.selectById(SPU_ID)).thenReturn(spu);
	}

	private ShipProductProfileDTO dto(String saleScope, ShipSkuProfile skuProfile) {
		ShipGoodsProfile profile = new ShipGoodsProfile();
		profile.setSpuId(SPU_ID);
		profile.setSaleScope(saleScope);
		profile.setImpaCode("751100");
		ShipProductProfileDTO dto = new ShipProductProfileDTO();
		dto.setSpuId(SPU_ID);
		dto.setProfile(profile);
		if (skuProfile != null) {
			dto.setSkuProfiles(List.of(skuProfile));
		}
		return dto;
	}

	private ShipSkuProfile skuProfile(String skuId, String purchaseUnit, Integer moq, Integer stepQty) {
		ShipSkuProfile skuProfile = new ShipSkuProfile();
		skuProfile.setSkuId(skuId);
		skuProfile.setPurchaseUnit(purchaseUnit);
		skuProfile.setMoq(moq);
		skuProfile.setStepQty(stepQty);
		return skuProfile;
	}

	@Test
	@DisplayName("商品统一后：缺少采购单位不再阻断保存（按基本单位处理）")
	void purchaseUnitIsOptionalAfterUnification() {
		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", null, 10, 5));
		ShipGoodsProfile saved = service.saveProfile(TENANT, d);
		assertNotNull(saved);
	}

	@Test
	@DisplayName("商品统一后：缺少 MOQ 不再阻断保存（按 1 处理）")
	void moqIsOptionalAfterUnification() {
		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", "箱", null, 5));
		ShipGoodsProfile saved = service.saveProfile(TENANT, d);
		assertNotNull(saved);
	}

	@Test
	@DisplayName("step_qty 必须大于 0")
	void stepQtyMustBePositive() {
		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", "箱", 10, 0));
		assertThrows(ArynBusinessException.class, () -> service.saveProfile(TENANT, d));
	}

	@Test
	@DisplayName("MOQ 必须是 step_qty 的整数倍")
	void moqMustBeMultipleOfStepQty() {
		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", "箱", 7, 5));
		assertThrows(ArynBusinessException.class, () -> service.saveProfile(TENANT, d));
	}

	@Test
	@DisplayName("商品统一后：缺少 IMPA/ISSA/内部编码不再阻断保存")
	void codeIsOptionalAfterUnification() {
		ShipGoodsProfile profile = new ShipGoodsProfile();
		profile.setSpuId(SPU_ID);
		profile.setSaleScope("2");
		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", "箱", 10, 5));
		d.getProfile().setImpaCode(null);
		ShipGoodsProfile saved = service.saveProfile(TENANT, d);
		assertNotNull(saved);
	}

	@Test
	@DisplayName("个人商品不强制 IMPA，合法保存")
	void personalScopeDoesNotRequireImpa() {
		ShipGoodsProfile profile = new ShipGoodsProfile();
		profile.setSpuId(SPU_ID);
		profile.setSaleScope("1");
		profile.setBarcode("6900000000001");
		ShipProductProfileDTO d = new ShipProductProfileDTO();
		d.setSpuId(SPU_ID);
		d.setProfile(profile);
		ShipGoodsProfile saved = service.saveProfile(TENANT, d);
		assertEquals("1", saved.getSaleScope());
	}

	@Test
	@DisplayName("销售范围取值不合法时拒绝保存")
	void invalidSaleScopeRejected() {
		ShipProductProfileDTO d = dto("9", skuProfile("sku-1", "箱", 10, 5));
		assertThrows(ArynBusinessException.class, () -> service.saveProfile(TENANT, d));
	}

	@Test
	@DisplayName("同一租户同一编码不能绑定两个有效 SKU")
	void codeCannotBindTwoSkus() {
		ProductCodeMapping conflict = new ProductCodeMapping();
		conflict.setSpuId("spu-other");
		conflict.setSkuId("sku-other");
		conflict.setCodeType(ProductCodeMapping.TYPE_IMPA);
		conflict.setCodeValue("751100");
		conflict.setStatus("1");
		when(productCodeMappingMapper.selectActiveByCodeValues(TENANT, List.of("751100"))).thenReturn(List.of(conflict));

		ShipProductProfileDTO d = dto("2", skuProfile("sku-1", "箱", 10, 5));
		ProductCodeMapping mapping = new ProductCodeMapping();
		mapping.setSpuId(SPU_ID);
		mapping.setSkuId("sku-1");
		mapping.setCodeType(ProductCodeMapping.TYPE_IMPA);
		mapping.setCodeValue("751100");
		d.setCodeMappings(List.of(mapping));

		assertThrows(ArynBusinessException.class, () -> service.saveProfile(TENANT, d));
	}

	@Test
	@DisplayName("sale_scope=3 资料合法保存并计算完整度")
	void bothScopeSavesWithCompleteness() {
		ShipProductProfileDTO d = dto("3", skuProfile("sku-1", "箱", 10, 5));
		d.getProfile().setNameEn("Test Item");
		d.getProfile().setStorageType("1");
		ShipGoodsProfile saved = service.saveProfile(TENANT, d);
		assertTrue(saved.getPublishCompleteness() > 0 && saved.getPublishCompleteness() <= 100);
	}

	@Test
	@DisplayName("完整度按船供关键资料字段填充比例计算")
	void completenessComputation() {
		ShipGoodsProfile profile = new ShipGoodsProfile();
		profile.setSaleScope("2");
		assertEquals(9, ShipProductProfileServiceImpl.computeCompleteness(profile));
		profile.setImpaCode("751100");
		profile.setNameEn("Item");
		assertTrue(ShipProductProfileServiceImpl.computeCompleteness(profile) > 9);
	}

	@Test
	@DisplayName("船供列表接口返回摘要，详情返回完整扩展资料")
	void listAndDetailShapes() {
		IPage<ShipProductSummaryVO> page = mock(IPage.class);
		ShipProductSummaryVO query = new ShipProductSummaryVO();
		service.shipSummaryPage(TENANT, page, query);
		verify(shipGoodsProfileMapper).selectShipSummaryPage(page, TENANT, query);

		when(shipGoodsProfileMapper.selectOne(any())).thenReturn(new ShipGoodsProfile());
		when(shipSkuProfileMapper.selectList(any())).thenReturn(List.of(new ShipSkuProfile()));
		when(productCodeMappingMapper.selectList(any())).thenReturn(List.of(new ProductCodeMapping()));
		assertEquals(1, service.listSkuProfiles(TENANT, SPU_ID).size());
		assertEquals(1, service.listCodeMappings(TENANT, SPU_ID).size());
	}

}
