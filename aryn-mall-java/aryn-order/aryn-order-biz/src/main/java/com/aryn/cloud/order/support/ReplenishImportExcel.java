package com.aryn.cloud.order.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.aryn.cloud.common.security.handler.ArynBusinessException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 补给清单 Excel 解析与模板列定义（订单域）。
 *
 * <p>模板与解析**共用同一份列定义**，避免模板改了一处、解析忘改另一处
 * （与商品批量导入 {@code ProductExcelConverter} 同一范式）。
 * 首行为中文标题，按标题映射字段，空行跳过。
 *
 * @author aryn
 * @since 2026/9/22
 */
public final class ReplenishImportExcel {

	/** 单个文件允许的最大数据行数 */
	public static final int MAX_ROWS = 2000;

	/** 模板列定义：title 为 Excel 表头文本，field 为行 DTO 字段 */
	public record Column(String title, String field) {
	}

	public static final List<Column> COLUMNS = List.of(new Column("商品编码", "code"), new Column("品名", "name"),
			new Column("规格", "spec"), new Column("数量", "quantity"), new Column("单位", "unit"),
			new Column("备注", "remark"));

	private static final Map<String, String> TITLE_TO_FIELD = COLUMNS.stream()
		.collect(LinkedHashMap::new, (map, column) -> map.put(column.title(), column.field()), Map::putAll);

	private ReplenishImportExcel() {
	}

	/**
	 * 解析 Excel 输入流（首行为标题行）为结构化行。
	 */
	public static List<ParsedRow> parse(InputStream inputStream) {
		List<ParsedRow> rows = new ArrayList<>();
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
				ParsedRow row = toParsedRow(record);
				if (!row.blank()) {
					rows.add(row);
				}
			}

			@Override
			public void doAfterAllAnalysed(AnalysisContext context) {
				// 全部解析完成，无需处理
			}
		}).sheet(0).headRowNumber(1).doRead();

		if (rows.isEmpty()) {
			throw new ArynBusinessException("Excel 未解析到数据行，请按模板填写后重试");
		}
		if (rows.size() > MAX_ROWS) {
			throw new ArynBusinessException("单个文件最多支持 " + MAX_ROWS + " 行数据，当前 " + rows.size() + " 行");
		}
		return rows;
	}

	/** 模板首行（下载用） */
	public static List<List<String>> templateHead() {
		return COLUMNS.stream().map(column -> List.of(column.title())).toList();
	}

	/**
	 * 按「字段 → 文本」映射构建解析行；行号由调用方按顺序编号，与报告一致。
	 */
	public static ParsedRow toParsedRow(Map<String, String> record) {
		ParsedRow row = new ParsedRow();
		row.setCode(orNull(record.get("code")));
		row.setName(orNull(record.get("name")));
		row.setSpec(orNull(record.get("spec")));
		row.setQuantityText(orNull(record.get("quantity")));
		row.setUnit(orNull(record.get("unit")));
		row.setRemark(orNull(record.get("remark")));
		return row;
	}

	/** 数值单元格可能出现 12.0 形式，去掉无意义的小数位 */
	public static String toText(Object value) {
		if (value == null) {
			return null;
		}
		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.matches("\\d+\\.0+")) {
			text = text.substring(0, text.indexOf('.'));
		}
		return text;
	}

	private static String orNull(String value) {
		return value != null && !value.isBlank() ? value.trim() : null;
	}

	/**
	 * 解析行：数量保留原文（是否合法由订单域按 MOQ/步长判定并给出建议），
	 * 空白行在解析阶段即被剔除。
	 */
	public static final class ParsedRow {

		private String code;

		private String name;

		private String spec;

		private String quantityText;

		private String unit;

		private String remark;

		public boolean blank() {
			return code == null && name == null && spec == null && quantityText == null && unit == null
					&& remark == null;
		}

		public String getCode() {
			return code;
		}

		public void setCode(String code) {
			this.code = code;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getSpec() {
			return spec;
		}

		public void setSpec(String spec) {
			this.spec = spec;
		}

		public String getQuantityText() {
			return quantityText;
		}

		public void setQuantityText(String quantityText) {
			this.quantityText = quantityText;
		}

		public String getUnit() {
			return unit;
		}

		public void setUnit(String unit) {
			this.unit = unit;
		}

		public String getRemark() {
			return remark;
		}

		public void setRemark(String remark) {
			this.remark = remark;
		}

	}

}
