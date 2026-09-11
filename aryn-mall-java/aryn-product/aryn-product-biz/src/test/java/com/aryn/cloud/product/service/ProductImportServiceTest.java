package com.aryn.cloud.product.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ProductCodeMapping;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;
import com.aryn.cloud.product.mapper.GoodsBrandMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.ProductChangeLogMapper;
import com.aryn.cloud.product.mapper.ProductCodeMappingMapper;
import com.aryn.cloud.product.mapper.ProductImportErrorMapper;
import com.aryn.cloud.product.mapper.ProductImportJobMapper;
import com.aryn.cloud.product.service.impl.ProductImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商品批量导入校验契约测试。
 */
class ProductImportServiceTest {

	private static final String TENANT = "tenant-1";

	private ProductImportServiceImpl service;

	private ProductImportErrorMapper errorMapper;

	private ProductCodeMappingMapper codeMappingMapper;

	private GoodsSkuMapper skuMapper;

	@BeforeEach
	void setUp() {
		ProductImportJobMapper jobMapper = mock(ProductImportJobMapper.class);
		errorMapper = mock(ProductImportErrorMapper.class);
		ProductChangeLogMapper changeLogMapper = mock(ProductChangeLogMapper.class);
		GoodsSpuMapper spuMapper = mock(GoodsSpuMapper.class);
		skuMapper = mock(GoodsSkuMapper.class);
		GoodsCategoryMapper categoryMapper = mock(GoodsCategoryMapper.class);
		GoodsBrandMapper brandMapper = mock(GoodsBrandMapper.class);
		codeMappingMapper = mock(ProductCodeMappingMapper.class);
		IShipProductProfileService shipProfileService = mock(IShipProductProfileService.class);

		// 类目/品牌默认存在
		GoodsCategory category = new GoodsCategory();
		category.setId("cat-1");
		when(categoryMapper.selectById("cat-1")).thenReturn(category);
		GoodsBrand brand = new GoodsBrand();
		brand.setId("brand-1");
		when(brandMapper.selectById("brand-1")).thenReturn(brand);

		service = new ProductImportServiceImpl(jobMapper, errorMapper, changeLogMapper, spuMapper, skuMapper,
				categoryMapper, brandMapper, codeMappingMapper, shipProfileService);
	}

	private ProductImportRowDTO row(String name) {
		ProductImportRowDTO row = new ProductImportRowDTO();
		row.setName(name);
		row.setSaleScope("1");
		return row;
	}

	private String firstErrorType(ProductImportPreviewVO vo) {
		return vo.getErrors().get(0).getErrorType();
	}

	@Test
	@DisplayName("空名称报 EMPTY_NAME")
	void emptyNameRejected() {
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(row("")));
		assertEquals("EMPTY_NAME", firstErrorType(vo));
		assertFalse(vo.getConfirmable());
	}

	@Test
	@DisplayName("同批次重复 IMPA 报 DUPLICATE_CODE")
	void duplicateCodeRejected() {
		ProductImportRowDTO r1 = row("商品A");
		r1.setImpaCode("751100");
		ProductImportRowDTO r2 = row("商品B");
		r2.setImpaCode("751100");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r1, r2));
		assertEquals("DUPLICATE_CODE", firstErrorType(vo));
		assertEquals(1, vo.getErrorRows());
	}

	@Test
	@DisplayName("同批次重复 SKU 报 DUPLICATE_SKU")
	void duplicateSkuRejected() {
		GoodsSku existing = new GoodsSku();
		existing.setId("sku-1");
		existing.setSpuId("spu-1");
		existing.setTenantId(TENANT);
		when(skuMapper.selectById("sku-1")).thenReturn(existing);
		ProductImportRowDTO r1 = row("商品A");
		r1.setMatchType("SKU");
		r1.setMatchValue("sku-1");
		ProductImportRowDTO r2 = row("商品B");
		r2.setMatchType("SKU");
		r2.setMatchValue("sku-1");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r1, r2));
		assertTrue(vo.getErrors().stream()
				.anyMatch(error -> ProductImportError.TYPE_DUPLICATE_SKU.equals(error.getErrorType())));
	}

	@Test
	@DisplayName("非法价格报 ILLEGAL_PRICE")
	void illegalPriceRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setSalesPrice(new BigDecimal("-1"));
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("ILLEGAL_PRICE", firstErrorType(vo));
	}

	@Test
	@DisplayName("非法库存报 ILLEGAL_STOCK")
	void illegalStockRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setStock(-5);
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("ILLEGAL_STOCK", firstErrorType(vo));
	}

	@Test
	@DisplayName("MOQ/step_qty 不合规报 ILLEGAL_QTY_RULE")
	void illegalQtyRuleRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setMoq(7);
		r.setStepQty(5);
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("ILLEGAL_QTY_RULE", firstErrorType(vo));
	}

	@Test
	@DisplayName("未知类目报 UNKNOWN_CATEGORY")
	void unknownCategoryRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setCategorySecondId("cat-missing");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("UNKNOWN_CATEGORY", firstErrorType(vo));
	}

	@Test
	@DisplayName("未知品牌报 UNKNOWN_BRAND")
	void unknownBrandRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setBrandId("brand-missing");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("UNKNOWN_BRAND", firstErrorType(vo));
	}

	@Test
	@DisplayName("更新目标编码不存在报 MISSING_UPDATE_TARGET")
	void missingUpdateTargetRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setMatchType("IMPA");
		r.setMatchValue("759999");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("MISSING_UPDATE_TARGET", firstErrorType(vo));
	}

	@Test
	@DisplayName("编码归属其他租户报 CROSS_TENANT_CODE")
	void crossTenantCodeRejected() {
		ProductCodeMapping mapping = new ProductCodeMapping();
		mapping.setTenantId("tenant-other");
		when(codeMappingMapper.selectByCodeGlobal(any(), eq("751100"))).thenReturn(mapping);
		ProductImportRowDTO r = row("商品A");
		r.setMatchType("IMPA");
		r.setMatchValue("751100");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("CROSS_TENANT_CODE", firstErrorType(vo));
	}

	@Test
	@DisplayName("全部合法行可预览确认")
	void validRowsPreview() {
		ProductImportRowDTO r = row("商品A");
		r.setSalesPrice(new BigDecimal("12.5"));
		r.setStock(100);
		r.setCategorySecondId("cat-1");
		r.setBrandId("brand-1");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals(1, vo.getSuccessRows());
		assertEquals(0, vo.getErrorRows());
		assertTrue(vo.getConfirmable());
	}

	@Test
	@DisplayName("确认导入时任务不存在拒绝")
	void confirmUnknownJobRejected() {
		assertThrows(ArynBusinessException.class,
				() -> service.confirmImport(TENANT, "job-missing", "a.csv", List.of(row("x")), "op", "op"));
	}

	@Test
	@DisplayName("预览错误行落库")
	void errorsPersisted() {
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(row("")));
		assertEquals(1, vo.getErrors().size());
		assertEquals(1, vo.getErrors().get(0).getRowNo());
	}

}
