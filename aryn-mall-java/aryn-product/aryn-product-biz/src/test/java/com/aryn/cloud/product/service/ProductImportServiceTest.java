package com.aryn.cloud.product.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsCategory;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.ProductImportError;
import com.aryn.cloud.product.api.vo.ProductImportPreviewVO;
import com.aryn.cloud.product.mapper.GoodsBrandMapper;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.mapper.ProductChangeLogMapper;
import com.aryn.cloud.product.mapper.ProductImportErrorMapper;
import com.aryn.cloud.product.mapper.ProductImportJobMapper;
import com.aryn.cloud.product.mapper.ProductImportRowMapper;
import com.aryn.cloud.product.service.impl.ProductImportServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商品批量导入校验契约测试。
 *
 * <p>船供资料列与 IMPA/内部编码更新定位已随船供化下线（2026-09-29），
 * 更新定位只支持 SKU 编号（{@code goods_sku.id}）。
 */
class ProductImportServiceTest {

	private static final String TENANT = "tenant-1";

	private ProductImportServiceImpl service;

	private GoodsSkuMapper skuMapper;

	private ProductImportJobMapper jobMapper;

	@BeforeEach
	void setUp() {
		jobMapper = mock(ProductImportJobMapper.class);
		ProductImportErrorMapper errorMapper = mock(ProductImportErrorMapper.class);
		ProductImportRowMapper rowMapper = mock(ProductImportRowMapper.class);
		ProductChangeLogMapper changeLogMapper = mock(ProductChangeLogMapper.class);
		GoodsSpuMapper spuMapper = mock(GoodsSpuMapper.class);
		skuMapper = mock(GoodsSkuMapper.class);
		GoodsCategoryMapper categoryMapper = mock(GoodsCategoryMapper.class);
		GoodsBrandMapper brandMapper = mock(GoodsBrandMapper.class);

		// 类目/品牌默认存在
		GoodsCategory category = new GoodsCategory();
		category.setId("cat-1");
		when(categoryMapper.selectById("cat-1")).thenReturn(category);
		GoodsBrand brand = new GoodsBrand();
		brand.setId("brand-1");
		when(brandMapper.selectById("brand-1")).thenReturn(brand);

		service = new ProductImportServiceImpl(jobMapper, rowMapper, errorMapper, changeLogMapper, spuMapper, skuMapper,
				categoryMapper, brandMapper, new ObjectMapper());
	}

	private ProductImportRowDTO row(String name) {
		ProductImportRowDTO row = new ProductImportRowDTO();
		row.setName(name);
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
	@DisplayName("同批次重复 SKU 编号报 DUPLICATE_SKU")
	void duplicateSkuRejected() {
		GoodsSku existing = new GoodsSku();
		existing.setId("sku-1");
		existing.setSpuId("spu-1");
		existing.setTenantId(TENANT);
		when(skuMapper.selectById("sku-1")).thenReturn(existing);
		ProductImportRowDTO r1 = row("商品A");
		r1.setMatchValue("sku-1");
		ProductImportRowDTO r2 = row("商品B");
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
	@DisplayName("更新目标 SKU 编号不存在报 MISSING_UPDATE_TARGET")
	void missingUpdateTargetRejected() {
		ProductImportRowDTO r = row("商品A");
		r.setMatchValue("sku-missing");
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(r));
		assertEquals("MISSING_UPDATE_TARGET", firstErrorType(vo));
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
		assertThrows(ArynBusinessException.class, () -> service.confirmImport(TENANT, "job-missing", "op", "op"));
	}

	@Test
	@DisplayName("确认导入时任务状态不可确认拒绝")
	void confirmWrongStatusRejected() {
		com.aryn.cloud.product.api.entity.ProductImportJob job = new com.aryn.cloud.product.api.entity.ProductImportJob();
		job.setId("job-1");
		job.setTenantId(TENANT);
		job.setStatus(com.aryn.cloud.product.api.entity.ProductImportJob.STATUS_COMPLETED);
		when(jobMapper.selectById("job-1")).thenReturn(job);
		assertThrows(ArynBusinessException.class, () -> service.confirmImport(TENANT, "job-1", "op", "op"));
	}

	@Test
	@DisplayName("预览错误行落库")
	void errorsPersisted() {
		ProductImportPreviewVO vo = service.preview(TENANT, "a.csv", List.of(row("")));
		assertEquals(1, vo.getErrors().size());
		assertEquals(1, vo.getErrors().get(0).getRowNo());
	}

}
