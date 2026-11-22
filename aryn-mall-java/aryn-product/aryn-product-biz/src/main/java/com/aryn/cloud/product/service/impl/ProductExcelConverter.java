package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.dto.ProductImportRowDTO;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品导入 Excel 解析与模板列定义。
 *
 * <p>模板与解析共用同一份列定义：首行为中文标题，解析按标题映射字段；
 * 数值单元格容错处理（去除小数位残留），空列忽略。
 *
 * @author aryn
 * @since 2026/9/12
 */
public final class ProductExcelConverter {

	/** 模板列定义：title 为 Excel 表头文本，field 为行 DTO 字段 */
	public record Column(String title, String field) {
	}

	// 船供资料列（英文名/销售范围/IMPA/ISSA/内部编码/条形码/采购单位/箱规/MOQ/步长/储存条件）
	// 已随船供化下线（2026-09-29）；更新定位只支持 SKU 编号，不再有匹配方式列。
	public static final List<Column> COLUMNS = List.of(new Column("商品中文名", "name"),
			new Column("更新匹配值(SKU编号)", "matchValue"), new Column("二级类目ID", "categorySecondId"),
			new Column("品牌ID", "brandId"), new Column("售价(元)", "salesPrice"), new Column("库存", "stock"),
            new Column("销售单位", "salesUnit"), new Column("基准量纲", "baseDimension"), new Column("基准单位", "baseUnit"),
            new Column("每销售单位基准量", "baseQtyPerSalesUnit"), new Column("度量精度(EXACT/APPROX/UNKNOWN)", "quantityPrecision"),
            new Column("起订量", "minOrderQty"), new Column("数量步长", "stepQty"), new Column("展示规格", "unitSpecText"), new Column("度量版本", "measurementRevision"));

	private static final Map<String, String> TITLE_TO_FIELD = COLUMNS.stream()
		.collect(LinkedHashMap::new, (map, column) -> map.put(column.title(), column.field()), Map::putAll);

	private static final List<String> TITLES = COLUMNS.stream().map(Column::title).toList();

	private ProductExcelConverter() {
	}

	/**
	 * 解析 Excel 输入流（首行为标题行）为结构化行。
	 */
	public static List<ProductImportRowDTO> parse(InputStream inputStream) {
		List<ProductImportRowDTO> rows = new ArrayList<>();
		EasyExcel.read(inputStream, new AnalysisEventListener<Map<Integer, String>>() {

			private final Map<Integer, String> headerIndex = new LinkedHashMap<>();

			@Override
			public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
				headMap.forEach((index, title) -> {
					if (title != null && TITLE_TO_FIELD.containsKey(title.trim())) {
						headerIndex.put(index, TITLE_TO_FIELD.get(title.trim()));
					}
				});
			}

			@Override
			public void invoke(Map<Integer, String> data, AnalysisContext context) {
				Map<String, String> record = new LinkedHashMap<>();
				data.forEach((index, value) -> {
					String field = headerIndex.get(index);
					if (field != null) {
						record.put(field, toText(value));
					}
				});
				ProductImportRowDTO row = toRowDTO(record);
				row.setRowNo(context.readRowHolder().getRowIndex() + 1);
				rows.add(row);
			}

			@Override
			public void doAfterAllAnalysed(AnalysisContext context) {
				// 全部解析完成，无需处理
			}
		}).sheet(0).headRowNumber(1).doRead();
		return rows;
	}

	/**
	 * 按“字段→文本”映射构建行 DTO，数值字段容错转换，空值忽略。
	 */
	public static ProductImportRowDTO toRowDTO(Map<String, String> record) {
		ProductImportRowDTO row = new ProductImportRowDTO();
		row.setName(orNull(record.get("name")));
		row.setMatchValue(orNull(record.get("matchValue")));
		row.setCategorySecondId(orNull(record.get("categorySecondId")));
		row.setBrandId(orNull(record.get("brandId")));
		row.setSalesPrice(toDecimal(record.get("salesPrice")));
		row.setStock(toInteger(record.get("stock")));
		row.setSalesUnit(orNull(record.get("salesUnit")));
		row.setBaseDimension(orNull(record.get("baseDimension")));
		row.setBaseUnit(orNull(record.get("baseUnit")));
		row.setBaseQtyPerSalesUnit(toDecimal(record.get("baseQtyPerSalesUnit")));
		row.setQuantityPrecision(orNull(record.get("quantityPrecision")));
		row.setMinOrderQty(toInteger(record.get("minOrderQty")));
		row.setStepQty(toInteger(record.get("stepQty")));
		row.setUnitSpecText(orNull(record.get("unitSpecText")));
		row.setMeasurementRevision(toLong(record.get("measurementRevision")));
		return row;
	}

	public static List<List<String>> templateHead() {
		return TITLES.stream().map(List::of).toList();
	}

	private static String toText(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		// 数值单元格可能出现 12.0 形式，去掉无意义的小数位
		if (text.matches("\\d+\\.0+")) {
			text = text.substring(0, text.indexOf('.'));
		}
		return text;
	}

	private static String orNull(String value) {
		return value != null && !value.isBlank() ? value.trim() : null;
	}

	private static BigDecimal toDecimal(String value) {
		String text = toText(value);
		if (text == null) {
			return null;
		}
		try {
			return new BigDecimal(text);
		}
		catch (NumberFormatException ex) {
			// 非法数值交给行级校验以“非法价格”语义报错，这里返回负值触发校验
			return new BigDecimal("-1");
		}
	}

    private static Long toLong(String value) {
        String text = toText(value);
        if (text == null) return null;
        try { return new BigDecimal(text).longValueExact(); }
        catch (NumberFormatException | ArithmeticException exception) { return -1L; }
    }

	private static Integer toInteger(String value) {
		String text = toText(value);
		if (text == null) {
			return null;
		}
		try {
			return Integer.valueOf(text);
		}
		catch (NumberFormatException ex) {
			// 非法整数交给行级校验，这里返回 -1 触发数量规则/库存校验
			return -1;
		}
	}

}
