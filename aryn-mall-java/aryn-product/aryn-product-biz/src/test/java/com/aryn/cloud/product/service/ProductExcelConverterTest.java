package com.aryn.cloud.product.service;

import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.aryn.cloud.product.service.impl.ProductExcelConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.excel.EasyExcel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 模板列定义与行转换契约测试。
 *
 * <p>船供资料列已随船供化下线（2026-09-29），模板缩为 6 列，
 * 更新定位只支持 SKU 编号。
 */
class ProductExcelConverterTest {

	@Test
	@DisplayName("模板列定义覆盖全部导入字段")
	void templateHeadCoversFields() {
		List<List<String>> head = ProductExcelConverter.templateHead();
		assertEquals("商品中文名", head.get(0).get(0));
		assertEquals("库存", head.get(head.size() - 1).get(0));
		assertEquals(6, head.size());
		assertEquals(ProductExcelConverter.COLUMNS.size(), head.size());
	}

	@Test
	@DisplayName("按标题映射转换行数据")
	void toRowDTOMapsFields() {
		Map<String, String> record = new LinkedHashMap<>();
		record.put("name", "缆绳 8字结");
		record.put("matchValue", "975000000000000001");
		record.put("salesPrice", "128.0");
		record.put("stock", "50.0");

		ProductImportRowDTO row = ProductExcelConverter.toRowDTO(record);
		assertEquals("缆绳 8字结", row.getName());
		assertEquals("975000000000000001", row.getMatchValue());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("128")));
		assertEquals(50, row.getStock());
	}

	@Test
	@DisplayName("空单元格转 null，非法数值转为可被行级校验识别的哨兵值")
	void toRowDTOToleratesBlankAndIllegal() {
		Map<String, String> record = new LinkedHashMap<>();
		record.put("name", "商品A");
		record.put("salesPrice", "abc");
		record.put("stock", " ");

		ProductImportRowDTO row = ProductExcelConverter.toRowDTO(record);
		assertNull(row.getMatchValue());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("-1")));
		assertNull(row.getStock());
		assertTrue(row.getCategorySecondId() == null);
	}

	@Test
	@DisplayName("写入 xlsx 后可按标题解析回结构化行（往返一致）")
	void parseRoundTrip() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		EasyExcel.write(out).sheet("商品导入").head(ProductExcelConverter.templateHead())
			.doWrite(List.of(List.of("缆绳", "975000000000000001", "", "", "128.0", "50.0")));
		List<ProductImportRowDTO> rows = ProductExcelConverter
			.parse(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(1, rows.size());
		ProductImportRowDTO row = rows.get(0);
		assertEquals("缆绳", row.getName());
		assertEquals("975000000000000001", row.getMatchValue());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("128")));
		assertEquals(50, row.getStock());
	}

}
