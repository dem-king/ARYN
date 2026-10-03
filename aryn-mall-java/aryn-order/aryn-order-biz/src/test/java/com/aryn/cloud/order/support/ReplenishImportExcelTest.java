package com.aryn.cloud.order.support;

import com.aryn.cloud.product.api.vo.ReplenishCatalogRowVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补给清单模板与解析的列口径测试。
 *
 * <p>核心不变量：**模板导出的列必须能被解析回去**。
 * 这两件事由同一份 COLUMNS 定义，但目录模板额外加了只读参考列
 * （库存/售价；起订量/步长已随船供包装资料下线移除），
 * 一旦列顺序错位，「数量」会被写进「规格」列，
 * 而客户完全看不出来 —— 只能靠断言钉住。
 */
class ReplenishImportExcelTest {

	@Test
	@DisplayName("目录模板表头：解析列在前，只读参考列在后")
	void catalogHeadKeepsParseColumnsFirst() {
		List<List<String>> head = ReplenishImportExcel.catalogHead();
		List<String> titles = head.stream().map(column -> column.get(0)).toList();

		// 前若干列必须与解析列定义逐字一致且同序
		List<String> parseTitles = ReplenishImportExcel.COLUMNS.stream()
			.map(ReplenishImportExcel.Column::title)
			.toList();
		assertEquals(parseTitles, titles.subList(0, parseTitles.size()));
		// 参考列接在后面，且不参与解析
		assertEquals(ReplenishImportExcel.REFERENCE_TITLES,
				titles.subList(parseTitles.size(), titles.size()));
		for (String reference : ReplenishImportExcel.REFERENCE_TITLES) {
			assertFalse(parseTitles.contains(reference),
					"参考列不能被解析：客户改了这几列不算数，混进去会污染数量与备注");
		}
	}

	@Test
	@DisplayName("目录模板行：数量列留空，参考列与表头一一对应")
	void catalogRowLeavesQuantityBlank() {
		ReplenishCatalogRowVO row = new ReplenishCatalogRowVO();
		row.setCategoryName("乳品烘焙/牛奶");
		row.setCode("975000000000000001");
		row.setName("鲜牛奶 950ml");
		row.setSpec("950ml；瓶装");
		row.setStock(120);
		row.setSalesPrice(new BigDecimal("12.50"));

		List<String> values = ReplenishImportExcel.catalogRow(row);

		assertEquals(ReplenishImportExcel.catalogHead().size(), values.size(),
				"行列数必须与表头一致，错位会把数量写进别的列");
		assertEquals("乳品烘焙/牛奶", values.get(0));
		assertEquals("975000000000000001", values.get(1));
		assertEquals("鲜牛奶 950ml", values.get(2));
		assertEquals("950ml；瓶装", values.get(3));
		// 第 5 列是「数量」：必须留空 —— 模板的用法就是客户只填要买的那几行，
		// 预填 0 或 1 会被当成真实采购量
		assertEquals("", values.get(4), "数量列必须留空");
		assertEquals("", values.get(5));
		assertEquals("120", values.get(6));
		assertEquals("12.5", values.get(7), "售价去掉无意义的小数位，12.50 展示为 12.5");
	}

	@Test
	@DisplayName("目录模板行：空字段写成空串，不写 null 字面量")
	void catalogRowWritesEmptyStringForNull() {
		ReplenishCatalogRowVO row = new ReplenishCatalogRowVO();
		row.setName("无编码商品");

		List<String> values = ReplenishImportExcel.catalogRow(row);

		assertTrue(values.stream().noneMatch("null"::equals),
				"null 字面量会被客户当成有效文本填回来");
		assertEquals("", values.get(0));
		assertEquals("", values.get(1));
	}

	@Test
	@DisplayName("解析：目录模板行按标题定位列，参考列不出现在解析结果里")
	void parseIgnoresReferenceColumns() {
		// 用 EasyExcel 生成真实 xlsx（含参考列），确认解析只读参与匹配的列
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		com.alibaba.excel.EasyExcel.write(out).sheet("补给清单")
			.head(ReplenishImportExcel.catalogHead())
			.doWrite(List.of(List.of("乳品烘焙/牛奶", "975000000000000001", "鲜牛奶 950ml", "950ml", "4", "冷藏",
					"120", "12.5")));

		List<ReplenishImportExcel.ParsedRow> parsed = ReplenishImportExcel.parse(
				new java.io.ByteArrayInputStream(out.toByteArray()));

		assertEquals(1, parsed.size());
		ReplenishImportExcel.ParsedRow row = parsed.get(0);
		assertEquals("975000000000000001", row.getCode(), "编码必须落到 code 字段");
		assertEquals("鲜牛奶 950ml", row.getName());
		assertEquals("950ml", row.getSpec());
		assertEquals("4", row.getQuantityText(), "数量必须落到数量字段，不能被参考列挤走");
		assertEquals("冷藏", row.getRemark(), "参考列不能污染备注");
	}

	@Test
	@DisplayName("解析：数量列留空的行保留下来（由分类判为未填数量，不在解析阶段丢弃）")
	void parseKeepsBlankQuantityRows() {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		com.alibaba.excel.EasyExcel.write(out).sheet("补给清单")
			.head(ReplenishImportExcel.catalogHead())
			.doWrite(List.of(
					// 客户只填了这一行
					List.of("乳品烘焙/牛奶", "975000000000000001", "鲜牛奶 950ml", "950ml", "4", "", "120", "12.5"),
					// 其余行数量留空 —— 这些行仍要保留行号，否则报告的行号与 Excel 视觉行对不上
					List.of("蔬果/蔬菜", "975000000000000002", "番茄", "2kg/份", "", "", "80", "6")));

		List<ReplenishImportExcel.ParsedRow> parsed = ReplenishImportExcel.parse(
				new java.io.ByteArrayInputStream(out.toByteArray()));

		assertEquals(2, parsed.size(), "留空数量的行不能在解析阶段被丢掉");
		assertEquals("4", parsed.get(0).getQuantityText());
		assertNull(parsed.get(1).getQuantityText());
	}

	private static void assertNull(Object value) {
		org.junit.jupiter.api.Assertions.assertNull(value);
	}

}
