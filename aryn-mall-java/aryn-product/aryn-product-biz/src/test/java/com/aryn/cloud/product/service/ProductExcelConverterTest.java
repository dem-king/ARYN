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
 */
class ProductExcelConverterTest {

	@Test
	@DisplayName("模板列定义覆盖全部导入字段")
	void templateHeadCoversFields() {
		List<List<String>> head = ProductExcelConverter.templateHead();
		assertEquals("商品中文名", head.get(0).get(0));
		assertEquals("储存条件(1-5)", head.get(head.size() - 1).get(0));
		assertEquals(ProductExcelConverter.COLUMNS.size(), head.size());
	}

	@Test
	@DisplayName("按标题映射转换行数据")
	void toRowDTOMapsFields() {
		Map<String, String> record = new LinkedHashMap<>();
		record.put("name", "缆绳 8字结");
		record.put("nameEn", "Mooring Rope");
		record.put("saleScope", "2");
		record.put("impaCode", "401201");
		record.put("salesPrice", "128.0");
		record.put("stock", "50.0");
		record.put("moq", "10.0");
		record.put("stepQty", "5.0");
		record.put("purchaseUnit", "卷");

		ProductImportRowDTO row = ProductExcelConverter.toRowDTO(record);
		assertEquals("缆绳 8字结", row.getName());
		assertEquals("Mooring Rope", row.getNameEn());
		assertEquals("2", row.getSaleScope());
		assertEquals("401201", row.getImpaCode());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("128")));
		assertEquals(50, row.getStock());
		assertEquals(10, row.getMoq());
		assertEquals(5, row.getStepQty());
		assertEquals("卷", row.getPurchaseUnit());
	}

	@Test
	@DisplayName("空单元格转 null，非法数值转为可被行级校验识别的哨兵值")
	void toRowDTOToleratesBlankAndIllegal() {
		Map<String, String> record = new LinkedHashMap<>();
		record.put("name", "商品A");
		record.put("salesPrice", "abc");
		record.put("stock", " ");

		ProductImportRowDTO row = ProductExcelConverter.toRowDTO(record);
		assertNull(row.getNameEn());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("-1")));
		assertNull(row.getStock());
		assertTrue(row.getImpaCode() == null);
	}

	@Test
	@DisplayName("写入 xlsx 后可按标题解析回结构化行（往返一致）")
	void parseRoundTrip() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		EasyExcel.write(out).sheet("商品导入").head(ProductExcelConverter.templateHead())
			.doWrite(List.of(List.of("缆绳", "Rope", "2", "401201", "", "", "", "", "", "", "", "128.0", "50.0", "卷", "1卷", "10.0", "5.0", "1")));
		List<ProductImportRowDTO> rows = ProductExcelConverter
			.parse(new ByteArrayInputStream(out.toByteArray()));
		assertEquals(1, rows.size());
		ProductImportRowDTO row = rows.get(0);
		assertEquals("缆绳", row.getName());
		assertEquals("Rope", row.getNameEn());
		assertEquals("2", row.getSaleScope());
		assertEquals("401201", row.getImpaCode());
		assertEquals(0, row.getSalesPrice().compareTo(new java.math.BigDecimal("128")));
		assertEquals(50, row.getStock());
		assertEquals("卷", row.getPurchaseUnit());
	}

}
