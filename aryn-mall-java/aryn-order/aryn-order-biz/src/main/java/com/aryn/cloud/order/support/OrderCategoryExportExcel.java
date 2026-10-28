package com.aryn.cloud.order.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.handler.RowWriteHandler;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import com.alibaba.excel.write.handler.context.SheetWriteHandlerContext;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.PurchaseSceneEnum;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * 管理端订单导出（按分类分组，供供应商报货/备货）。
 *
	 * <p>三个工作表：
	 * <ul>
	 *   <li><b>订单明细</b>：一行一个商品，订单内商品按分类分组排列，每个分类块尾部跟一行「小计」，
	 *       订单末尾跟一行「订单合计」；订单号~收货地址的订单级列整单合并、分类名按分类块合并显示。
	 *       合并只作用于显示层，非首行单元格仍保留原值，筛选/透视照常按逐行取值（代价是合并列无法再整表排序）。</li>
	 *   <li><b>分类汇总</b>：跨订单按分类聚合的件数/金额，末尾附合计行。</li>
	 *   <li><b>订单分类小计</b>：每单 × 每分类一行的小计长表，订单号/下单时间/收货人按单合并显示，
	 *       供按订单对账、核对单笔订单的分类构成。</li>
	 * </ul>
 *
 * <p>不同分类通常对应不同供应商，因此每个分类在整份工作簿中使用同一底色
 * （明细整行着色、汇总表只给分类单元格着色），分类小计加粗灰底、
 * 订单/全局合计更深灰底并加双线上边框，便于按颜色和分隔线快速切分报货。
 *
 * <p>表头与数据行都走动态 {@code List<List<...>>}，因为明细表混合了
 * 商品行/小计行/合计行三种行型，注解实体表达不了这种结构。
 *
 * @author aryn
 * @since 2026/10/2
 */
public final class OrderCategoryExportExcel {

	/** 无分类商品的归组名：order_item 不落分类快照，商品被删或未归类时落到这里 */
	public static final String UNCATEGORIZED = "未分类";

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private static final String MONEY_FORMAT = "#,##0.00";

	private static final String QUANTITY_FORMAT = "#,##0";

	private static final String COUNT_FORMAT = "0";

	private static final String FONT_NAME = "微软雅黑";

	private static final List<String> DETAIL_HEAD = List.of("订单号", "下单时间", "购买场景", "配送方式", "收货人",
			"收货人电话", "收货地址", "分类", "商品名称", "规格", "单价（元）", "数量", "金额（元）", "订购人", "备注");

	private static final List<String> SUMMARY_HEAD = List.of("分类", "商品种数", "总数量", "总金额（元）");

	private static final List<String> ORDER_SUMMARY_HEAD = List.of("订单号", "下单时间", "收货人", "分类", "商品种数",
			"总数量", "总金额（元）");

	/**
	 * 列宽（字符数，中文按 2 个字符估算）。明细表刻意给地址/品名/规格/备注留出换行空间，
	 * 避免默认列宽把「下单时间」这类表头挤成两行、把地址截断。
	 */
	private static final int[] DETAIL_WIDTHS = {24, 20, 14, 18, 12, 15, 38, 22, 32, 22, 16, 10, 16, 16, 26};

	private static final int[] SUMMARY_WIDTHS = {28, 12, 12, 16};

	private static final int[] ORDER_SUMMARY_WIDTHS = {24, 20, 13, 22, 12, 11, 15};

	private static final String HEADER_FILL = "305496";

	private static final String SUBTOTAL_FILL = "D9D9D9";

	private static final String TOTAL_FILL = "A6A6A6";

	/** 未分类提示色：比普通分类更醒目，提醒运营补分类 */
	private static final String UNCATEGORIZED_FILL = "FFE699";

	/**
	 * 分类底色盘：按分类名拼音序轮转分配，同一分类在整份工作簿中颜色一致。
	 *
	 * <p>使用 RGB 而不是旧版索引色盘，避免 Excel/WPS/系统预览对索引色的映射差异；
	 * 全部选浅色，黑白打印时仍可依靠小计分隔线辨认。
	 */
	private static final List<String> CATEGORY_PALETTE = List.of("DDEBF7", "E2EFDA", "FFF2CC", "FCE4D6", "E4DFEC",
			"D9F2F2", "FBE2E5", "EDE7D9");

	private static final Set<Integer> DETAIL_WRAP_COLUMNS = Set.of(6, 7, 8, 9, 14);

	private OrderCategoryExportExcel() {
	}

	/**
	 * 写出导出 Excel
	 * @param orders 订单列表（商品行需已回填分类名）
	 * @param outputStream 输出流（由调用方负责关闭）
	 */
	public static void write(List<OrderInfo> orders, OutputStream outputStream) {
		Map<String, String> categoryColors = categoryColors(orders);
		SheetData detail = detailData(orders);
		SheetData summary = summaryData(orders);
		SheetData orderSummary = orderSummaryData(orders);
		try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
			excelWriter.write(detail.rows(), writeSheet(0, "订单明细", DETAIL_HEAD, detail, DETAIL_WIDTHS,
					categoryColors, ColumnProfile.DETAIL));
			excelWriter.write(summary.rows(), writeSheet(1, "分类汇总", SUMMARY_HEAD, summary, SUMMARY_WIDTHS,
					categoryColors, ColumnProfile.SUMMARY));
			excelWriter.write(orderSummary.rows(), writeSheet(2, "订单分类小计", ORDER_SUMMARY_HEAD, orderSummary,
					ORDER_SUMMARY_WIDTHS, categoryColors, ColumnProfile.ORDER_SUMMARY));
		}
	}

	private static WriteSheet writeSheet(int sheetNo, String sheetName, List<String> head, SheetData data,
			int[] widths, Map<String, String> categoryColors, ColumnProfile profile) {
		return EasyExcel.writerSheet(sheetNo, sheetName)
			.head(head.stream().map(List::of).toList())
			.registerWriteHandler(new SheetStyler(profile, data.metas(), widths, categoryColors))
			.build();
	}

	private static SheetData detailData(List<OrderInfo> orders) {
		List<List<Object>> rows = new ArrayList<>();
		List<RowMeta> metas = new ArrayList<>();
		for (OrderInfo order : orders) {
			List<OrderItemEntity> items = itemsOf(order);
			for (Map.Entry<String, List<OrderItemEntity>> entry : groupByCategory(items).entrySet()) {
				int categoryQuantity = 0;
				BigDecimal categoryAmount = BigDecimal.ZERO;
				for (OrderItemEntity item : entry.getValue()) {
					categoryQuantity = categoryQuantity + nullSafeQuantity(item);
					categoryAmount = categoryAmount.add(nullSafeTotalPrice(item));
					rows.add(detailRow(order, entry.getKey(), item));
					metas.add(RowMeta.item(entry.getKey()));
				}
				rows.add(subtotalRow(order, entry.getKey(), categoryQuantity, categoryAmount));
				metas.add(RowMeta.subtotal(entry.getKey()));
			}
			rows.add(orderTotalRow(order, items));
			metas.add(RowMeta.orderTotal());
		}
		return new SheetData(rows, metas);
	}

	private static List<Object> detailRow(OrderInfo order, String categoryName, OrderItemEntity item) {
		List<Object> row = new ArrayList<>(DETAIL_HEAD.size());
		row.add(order.getOrderNo());
		row.add(formatTime(order.getCreateTime()));
		row.add(sceneText(order.getPurchaseScene()));
		row.add(deliveryWayText(order.getDeliveryWay()));
		row.add(order.getRecipientName());
		row.add(order.getRecipientPhone());
		row.add(recipientAddress(order));
		row.add(categoryName);
		row.add(item.getSpuName());
		row.add(item.getSpecsInfo());
		row.add(item.getSalesPrice());
		row.add(item.getBuyQuantity());
		row.add(item.getTotalPrice());
		row.add(item.getContributorName());
		row.add(item.getMemberRemark());
		return row;
	}

	private static List<Object> subtotalRow(OrderInfo order, String categoryName, int quantity, BigDecimal amount) {
		List<Object> row = emptyDetailRow();
		row.set(0, order.getOrderNo());
		row.set(7, categoryName);
		row.set(8, categoryName + " 小计");
		row.set(11, quantity);
		row.set(12, amount);
		return row;
	}

	private static List<Object> orderTotalRow(OrderInfo order, List<OrderItemEntity> items) {
		int quantity = items.stream().mapToInt(OrderCategoryExportExcel::nullSafeQuantity).sum();
		BigDecimal amount = items.stream()
			.map(OrderCategoryExportExcel::nullSafeTotalPrice)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		List<Object> row = emptyDetailRow();
		row.set(0, order.getOrderNo());
		row.set(8, "订单合计");
		row.set(11, quantity);
		row.set(12, amount);
		return row;
	}

	private static List<Object> emptyDetailRow() {
		List<Object> row = new ArrayList<>(DETAIL_HEAD.size());
		for (int i = 0; i < DETAIL_HEAD.size(); i++) {
			row.add(null);
		}
		return row;
	}

	/**
	 * 订单内商品按分类归组；组间按分类名排序，「未分类」固定排最后，
	 * 组内保持下单商品行的原始顺序（共享采购按人拆行的语义不被打乱）。
	 */
	private static Map<String, List<OrderItemEntity>> groupByCategory(List<OrderItemEntity> items) {
		Map<String, List<OrderItemEntity>> grouped = new LinkedHashMap<>();
		for (OrderItemEntity item : items) {
			grouped.computeIfAbsent(categoryOf(item), key -> new ArrayList<>()).add(item);
		}
		Map<String, List<OrderItemEntity>> sorted = new LinkedHashMap<>();
		// 与管理端详情页同口径：分类名按中文拼音序，「未分类」固定排最后
		Collator collator = Collator.getInstance(Locale.CHINA);
		grouped.entrySet()
			.stream()
			.sorted(Comparator
				.comparing((Map.Entry<String, List<OrderItemEntity>> entry) -> entry.getKey()
					.equals(UNCATEGORIZED))
				.thenComparing(Map.Entry::getKey, collator))
			.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
		return sorted;
	}

	/** 跨订单按分类聚合：分类 → 商品种数（去重 spuId）/ 总件数 / 总金额，末尾补合计行 */
	private static SheetData summaryData(List<OrderInfo> orders) {
		Map<String, Integer> quantityByCategory = new LinkedHashMap<>();
		Map<String, BigDecimal> amountByCategory = new LinkedHashMap<>();
		Map<String, Set<String>> spuIdsByCategory = new LinkedHashMap<>();
		Set<String> allSpuIds = new HashSet<>();
		int totalQuantity = 0;
		BigDecimal totalAmount = BigDecimal.ZERO;
		for (OrderInfo order : orders) {
			for (OrderItemEntity item : itemsOf(order)) {
				String category = categoryOf(item);
				int quantity = nullSafeQuantity(item);
				BigDecimal amount = nullSafeTotalPrice(item);
				quantityByCategory.merge(category, quantity, Integer::sum);
				amountByCategory.merge(category, amount, BigDecimal::add);
				spuIdsByCategory.computeIfAbsent(category, key -> new LinkedHashSet<>()).add(item.getSpuId());
				allSpuIds.add(item.getSpuId());
				totalQuantity = totalQuantity + quantity;
				totalAmount = totalAmount.add(amount);
			}
		}
		List<List<Object>> rows = new ArrayList<>();
		List<RowMeta> metas = new ArrayList<>();
		amountByCategory.entrySet()
			.stream()
			.sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
			.forEach(entry -> {
				String category = entry.getKey();
				List<Object> row = new ArrayList<>(SUMMARY_HEAD.size());
				row.add(category);
				row.add(spuIdsByCategory.get(category).size());
				row.add(quantityByCategory.get(category));
				row.add(entry.getValue());
				rows.add(row);
				metas.add(RowMeta.data(category, false));
			});
		List<Object> totalRow = new ArrayList<>(SUMMARY_HEAD.size());
		totalRow.add("合计");
		totalRow.add(allSpuIds.size());
		totalRow.add(totalQuantity);
		totalRow.add(totalAmount);
		rows.add(totalRow);
		metas.add(RowMeta.grandTotal());
		return new SheetData(rows, metas);
	}

	private static String categoryOf(OrderItemEntity item) {
		return item.getCategoryName() == null || item.getCategoryName().isBlank()
				? UNCATEGORIZED : item.getCategoryName();
	}

	/**
	 * 每单 × 每分类一行的小计长表：行序 = 订单序（入参序，SQL 已按下单时间倒序），
	 * 单内分类序与明细表同口径（拼音序，「未分类」最后）；每单最后一行的下边框加粗，
	 * 便于在长表里按订单切分。
	 */
	private static SheetData orderSummaryData(List<OrderInfo> orders) {
		List<List<Object>> rows = new ArrayList<>();
		List<RowMeta> metas = new ArrayList<>();
		for (OrderInfo order : orders) {
			List<Map.Entry<String, List<OrderItemEntity>>> categories = new ArrayList<>(
					groupByCategory(itemsOf(order)).entrySet());
			for (int i = 0; i < categories.size(); i++) {
				Map.Entry<String, List<OrderItemEntity>> entry = categories.get(i);
				int quantity = 0;
				BigDecimal amount = BigDecimal.ZERO;
				Set<String> spuIds = new LinkedHashSet<>();
				for (OrderItemEntity item : entry.getValue()) {
					quantity = quantity + nullSafeQuantity(item);
					amount = amount.add(nullSafeTotalPrice(item));
					spuIds.add(item.getSpuId());
				}
				List<Object> row = new ArrayList<>(ORDER_SUMMARY_HEAD.size());
				row.add(order.getOrderNo());
				row.add(formatTime(order.getCreateTime()));
				row.add(order.getRecipientName());
				row.add(entry.getKey());
				row.add(spuIds.size());
				row.add(quantity);
				row.add(amount);
				rows.add(row);
				metas.add(RowMeta.data(entry.getKey(), i == categories.size() - 1));
			}
		}
		return new SheetData(rows, metas);
	}

	/**
	 * 分类 → 底色。按分类名拼音序分配色盘，保证同一分类在明细/汇总/订单小计三张表里颜色一致；
	 * 「未分类」用浅棕提示归类缺失。
	 */
	private static Map<String, String> categoryColors(List<OrderInfo> orders) {
		Set<String> categories = new TreeSet<>(Collator.getInstance(Locale.CHINA));
		for (OrderInfo order : orders) {
			for (OrderItemEntity item : itemsOf(order)) {
				categories.add(categoryOf(item));
			}
		}
		Map<String, String> colors = new LinkedHashMap<>();
		int index = 0;
		for (String category : categories) {
			if (UNCATEGORIZED.equals(category)) {
				colors.put(category, UNCATEGORIZED_FILL);
			}
			else {
				colors.put(category, CATEGORY_PALETTE.get(index % CATEGORY_PALETTE.size()));
				index++;
			}
		}
		return colors;
	}

	private static List<OrderItemEntity> itemsOf(OrderInfo order) {
		return order.getOrderItemList() == null ? List.of() : order.getOrderItemList();
	}

	private static int nullSafeQuantity(OrderItemEntity item) {
		return item.getBuyQuantity() == null ? 0 : item.getBuyQuantity();
	}

	private static BigDecimal nullSafeTotalPrice(OrderItemEntity item) {
		return item.getTotalPrice() == null ? BigDecimal.ZERO : item.getTotalPrice();
	}

	private static String recipientAddress(OrderInfo order) {
		StringBuilder address = new StringBuilder();
		for (String part : new String[] { order.getRecipientProvince(), order.getRecipientCity(),
				order.getRecipientArea(), order.getRecipientAddress() }) {
			if (part != null && !part.isBlank()) {
				address.append(part);
			}
		}
		return address.toString();
	}

	/**
	 * 购买场景：2 船供采购 / 3 企业采购，其余（含历史版本未落场景的空值）为海员个人购买。
	 *
	 * <p>与管理端列表、订单详情页共用同一口径：场景是订单固有属性，空值等价于个人购买，
	 * 导出不得留空，否则同一张订单在管理端显示「个人购买」、在 Excel 里却是空单元格。
	 */
	private static String sceneText(String scene) {
		if (PurchaseSceneEnum.SHIP_SUPPLY.getCode().equals(scene)) {
			return "船供采购";
		}
		if (PurchaseSceneEnum.ENTERPRISE.getCode().equals(scene)) {
			return "企业采购";
		}
		return "个人购买";
	}

	/** 配送方式：与 delivery_way 字典对齐（1 普通快递 / 2 上门自提 / 3 公司配送（岸上）/ 4 公司配送（到船）） */
	private static String deliveryWayText(String way) {
		if ("1".equals(way)) {
			return "普通快递";
		}
		if ("2".equals(way)) {
			return "上门自提";
		}
		if ("3".equals(way)) {
			return "公司配送（岸上）";
		}
		if ("4".equals(way)) {
			return "公司配送（到船）";
		}
		return way == null ? "" : way;
	}

	private static String formatTime(LocalDateTime time) {
		return time == null ? "" : TIME_FORMATTER.format(time);
	}

	private enum RowKind {

		/** 商品行 */
		ITEM,
		/** 分类小计行 */
		SUBTOTAL,
		/** 订单合计行 */
		ORDER_TOTAL,
		/** 普通数据行（汇总表 / 订单分类小计） */
		DATA,
		/** 全局合计行 */
		GRAND_TOTAL

	}

	private enum ColumnProfile {

		DETAIL(7, true, false),
		SUMMARY(0, false, true),
		ORDER_SUMMARY(3, false, true);

		/** 分类所在列：合计/小计标签与分类底色都依赖它 */
		private final int categoryColumn;

		private final boolean autoFilter;

		private final boolean fitToPageWidth;

		ColumnProfile(int categoryColumn, boolean autoFilter, boolean fitToPageWidth) {
			this.categoryColumn = categoryColumn;
			this.autoFilter = autoFilter;
			this.fitToPageWidth = fitToPageWidth;
		}

	}

	/**
	 * @param category 分类名，用于取底色（小计/合计行为空）
	 * @param groupEnd 是否为分组最后一行，是则下边框加粗
	 */
	private record RowMeta(RowKind kind, String category, boolean groupEnd) {

		static RowMeta item(String category) {
			return new RowMeta(RowKind.ITEM, category, false);
		}

		static RowMeta subtotal(String category) {
			return new RowMeta(RowKind.SUBTOTAL, category, false);
		}

		static RowMeta orderTotal() {
			return new RowMeta(RowKind.ORDER_TOTAL, null, true);
		}

		static RowMeta data(String category, boolean groupEnd) {
			return new RowMeta(RowKind.DATA, category, groupEnd);
		}

		static RowMeta grandTotal() {
			return new RowMeta(RowKind.GRAND_TOTAL, null, true);
		}

	}

	private record SheetData(List<List<Object>> rows, List<RowMeta> metas) {
	}

		/**
		 * 工作表样式：表头、分类色带、小计/合计强调、数字格式、列宽、冻结、筛选、
		 * 订单号/分类列纵向合并与打印设置。
		 *
		 * <p>行型信息由 {@link RowMeta} 按数据行下标提供，EasyExcel 回调里的
		 * {@code rowIndex} 含表头行，故数据行下标为 {@code rowIndex - 1}。
		 */
	private static final class SheetStyler implements CellWriteHandler, SheetWriteHandler, RowWriteHandler {

		private final ColumnProfile profile;

		private final List<RowMeta> metas;

		private final int[] widths;

		private final Map<String, String> categoryColors;

		private final Map<String, CellStyle> styles = new HashMap<>();

		private final Map<String, Font> fonts = new HashMap<>();

		private SheetStyler(ColumnProfile profile, List<RowMeta> metas, int[] widths,
				Map<String, String> categoryColors) {
			this.profile = profile;
			this.metas = metas;
			this.widths = widths;
			this.categoryColors = categoryColors;
		}

		@Override
		public void afterSheetCreate(SheetWriteHandlerContext context) {
			Sheet sheet = context.getWriteSheetHolder().getSheet();
			for (int i = 0; i < widths.length; i++) {
				sheet.setColumnWidth(i, widths[i] * 256);
			}
			// 冻结表头与订单号列：横向滚动看地址/金额时仍能对上订单
			sheet.createFreezePane(1, 1);
			if (profile.autoFilter) {
				sheet.setAutoFilter(new CellRangeAddress(0, metas.size(), 0, widths.length - 1));
			}
			// 订单级列/分类列纵向合并（仅显示层，非首行保留原值，筛选/透视仍按逐行取值）。
			// 区域按连续行块生成、互不重叠，走 Unsafe 跳过 POI 逐区域重叠校验，
			// 否则 5000 单上限下数万个区域的 O(n²) 校验会把导出拖到分钟级
			for (CellRangeAddress region : mergeRegions()) {
				sheet.addMergedRegionUnsafe(region);
			}
			sheet.setRepeatingRows(CellRangeAddress.valueOf("1:1"));
			PrintSetup print = sheet.getPrintSetup();
			print.setLandscape(true);
			if (profile.fitToPageWidth) {
				print.setPaperSize(PrintSetup.A4_PAPERSIZE);
				print.setFitWidth((short) 1);
				print.setFitHeight((short) 0);
				sheet.setFitToPage(true);
			}
			else {
				print.setPaperSize(PrintSetup.A3_PAPERSIZE);
				// 明细表列多，A3 横向分页打印时重复订单号列，避免后续页认不出是谁的订单
				sheet.setRepeatingColumns(CellRangeAddress.valueOf("A:A"));
			}
			sheet.setMargin(Sheet.LeftMargin, 0.3);
			sheet.setMargin(Sheet.RightMargin, 0.3);
			sheet.setMargin(Sheet.TopMargin, 0.5);
			sheet.setMargin(Sheet.BottomMargin, 0.5);
			sheet.getFooter().setCenter("第 &P 页 / 共 &N 页");
		}

		@Override
		public void afterRowDispose(RowWriteHandlerContext context) {
			if (context.getRowIndex() == null || context.getRowIndex() != 0 || context.getRow() == null) {
				return;
			}
			Row row = context.getRow();
			row.setHeightInPoints(34);
			// EasyExcel 的默认表头样式在单元格回调之后才落盘，表头样式放到整行写完后统一覆盖
			CellStyle header = styles.computeIfAbsent("HEAD", key -> headerStyle(row.getSheet().getWorkbook()));
			for (int i = 0; i < widths.length; i++) {
				Cell cell = row.getCell(i);
				if (cell != null) {
					cell.setCellStyle(header);
				}
			}
		}

		@Override
		public void afterCellDispose(CellWriteHandlerContext context) {
			Cell cell = context.getCell();
			if (cell == null || context.getRowIndex() == null) {
				return;
			}
			Workbook workbook = cell.getSheet().getWorkbook();
			// 表头固定在第 0 行，样式在 afterRowDispose 里统一覆盖（EasyExcel 默认表头样式晚于单元格回调落盘）
			int dataIndex = context.getRowIndex() - 1;
			if (dataIndex < 0 || dataIndex >= metas.size()) {
				return;
			}
			int column = context.getColumnIndex() == null ? 0 : context.getColumnIndex();
			cell.setCellStyle(style(workbook, metas.get(dataIndex), column));
		}

		/**
		 * 纵向合并区域：订单块（上一单合计行之后到本单合计行）内合并订单级列——
		 * 明细表为订单号/下单时间/购买场景/配送方式/收货人/电话/地址（列 0-6），
		 * 订单分类小计表为订单号/下单时间/收货人（列 0-2）；明细表另按分类块
		 * （连续同分类的商品行 + 收尾小计行）合并分类列。合并只登记显示层区域，
		 * 非首行单元格的值原样保留，筛选/透视仍按逐行取值匹配。
		 */
		private List<CellRangeAddress> mergeRegions() {
			if (profile == ColumnProfile.SUMMARY) {
				return List.of();
			}
			List<CellRangeAddress> regions = new ArrayList<>();
			// 订单级列：订单块从上一单合计行之后开始，到本单合计行（groupEnd）结束
			int orderStart = 0;
			for (int i = 0; i < metas.size(); i++) {
				if (metas.get(i).groupEnd()) {
					for (int column : orderMergeColumns()) {
						addRowSpan(regions, column, orderStart, i);
					}
					orderStart = i + 1;
				}
			}
			if (profile == ColumnProfile.DETAIL) {
				// 分类列：块内分类名相同、块尾必是小计行；单内分类不重复、订单间有合计行隔开，块不会跨单
				int blockStart = 0;
				while (blockStart < metas.size()) {
					String category = metas.get(blockStart).category();
					int blockEnd = blockStart;
					while (blockEnd + 1 < metas.size()
							&& Objects.equals(category, metas.get(blockEnd + 1).category())) {
						blockEnd++;
					}
					if (category != null && metas.get(blockEnd).kind() == RowKind.SUBTOTAL) {
						addRowSpan(regions, profile.categoryColumn, blockStart, blockEnd);
					}
					blockStart = blockEnd + 1;
				}
			}
			return regions;
		}

		/** 订单级合并列：明细表 = 表头前 7 列（订单号~收货地址），订单分类小计表 = 前 3 列（订单号/下单时间/收货人） */
		private int[] orderMergeColumns() {
			if (profile == ColumnProfile.DETAIL) {
				return new int[] {0, 1, 2, 3, 4, 5, 6};
			}
			return new int[] {0, 1, 2};
		}

		/** 数据行下标转物理行号（表头占第 0 行，偏移 +1）；单行块不构成合并区域 */
		private static void addRowSpan(List<CellRangeAddress> regions, int column, int firstRow, int lastRow) {
			if (lastRow > firstRow) {
				regions.add(new CellRangeAddress(firstRow + 1, lastRow + 1, column, column));
			}
		}

		private CellStyle style(Workbook workbook, RowMeta meta, int column) {
			String categoryColor = categoryColors.get(meta.category());
			String key = meta.kind().name() + '|' + column + '|' + (categoryColor == null ? "" : categoryColor) + '|'
					+ meta.groupEnd();
			return styles.computeIfAbsent(key, ignored -> dataStyle(workbook, meta, column, categoryColor));
		}

		private CellStyle headerStyle(Workbook workbook) {
			CellStyle style = workbook.createCellStyle();
			style.setFont(font(workbook, true, IndexedColors.WHITE, (short) 11));
			applyFill(style, HEADER_FILL);
			style.setAlignment(HorizontalAlignment.CENTER);
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			style.setWrapText(true);
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setTopBorderColor(IndexedColors.WHITE.getIndex());
			style.setBottomBorderColor(IndexedColors.WHITE.getIndex());
			style.setLeftBorderColor(IndexedColors.WHITE.getIndex());
			style.setRightBorderColor(IndexedColors.WHITE.getIndex());
			return style;
		}

		private CellStyle dataStyle(Workbook workbook, RowMeta meta, int column, String categoryColor) {
			CellStyle style = workbook.createCellStyle();
			boolean bold = meta.kind() == RowKind.SUBTOTAL || meta.kind() == RowKind.ORDER_TOTAL
					|| meta.kind() == RowKind.GRAND_TOTAL || column == profile.categoryColumn;
			style.setFont(font(workbook, bold, IndexedColors.BLACK, (short) 10));
			style.setVerticalAlignment(VerticalAlignment.CENTER);
			// 分类列在三张表里都换行，避免「休闲零食/休闲零食/膨化肉脯」这类长分类被截断
			boolean wrap = column == profile.categoryColumn
					|| (profile == ColumnProfile.DETAIL && DETAIL_WRAP_COLUMNS.contains(column));
			style.setWrapText(wrap);

			String fill = fillColor(meta, column, categoryColor);
			if (fill != null) {
				applyFill(style, fill);
			}
			applyBorders(style, meta);
			applyAlignment(style, meta, column);
			applyNumberFormat(style, workbook, column);
			return style;
		}

		private String fillColor(RowMeta meta, int column, String categoryColor) {
			if (meta.kind() == RowKind.SUBTOTAL) {
				return SUBTOTAL_FILL;
			}
			if (meta.kind() == RowKind.ORDER_TOTAL || meta.kind() == RowKind.GRAND_TOTAL) {
				return TOTAL_FILL;
			}
			if (profile == ColumnProfile.DETAIL) {
				return categoryColor;
			}
			return column == profile.categoryColumn ? categoryColor : null;
		}

		private static void applyFill(CellStyle style, String rgb) {
			if (style instanceof XSSFCellStyle xssfStyle) {
				xssfStyle.setFillForegroundColor(new XSSFColor(toRgb(rgb)));
				style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			}
		}

		private static byte[] toRgb(String hex) {
			return new byte[] { (byte) Integer.parseInt(hex.substring(0, 2), 16),
					(byte) Integer.parseInt(hex.substring(2, 4), 16),
					(byte) Integer.parseInt(hex.substring(4, 6), 16) };
		}

		private void applyBorders(CellStyle style, RowMeta meta) {
			style.setBorderTop(BorderStyle.THIN);
			style.setBorderBottom(BorderStyle.THIN);
			style.setBorderLeft(BorderStyle.THIN);
			style.setBorderRight(BorderStyle.THIN);
			style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
			style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
			style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
			style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
			if (meta.kind() == RowKind.SUBTOTAL) {
				style.setBorderTop(BorderStyle.MEDIUM);
				style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
			}
			else if (meta.kind() == RowKind.ORDER_TOTAL || meta.kind() == RowKind.GRAND_TOTAL) {
				style.setBorderTop(BorderStyle.DOUBLE);
				style.setTopBorderColor(IndexedColors.BLACK.getIndex());
			}
			if (meta.groupEnd()) {
				style.setBorderBottom(BorderStyle.MEDIUM);
				style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
			}
		}

		private void applyAlignment(CellStyle style, RowMeta meta, int column) {
			HorizontalAlignment alignment = HorizontalAlignment.LEFT;
			if (profile == ColumnProfile.DETAIL) {
				if (column == 2 || column == 3 || column == 4 || column == 5 || column == 11 || column == 13) {
					alignment = HorizontalAlignment.CENTER;
				}
				else if (column == 10 || column == 12) {
					alignment = HorizontalAlignment.RIGHT;
				}
				else if ((meta.kind() == RowKind.SUBTOTAL || meta.kind() == RowKind.ORDER_TOTAL)
						&& column == 8) {
					// 小计/合计标签贴近右侧的数量与金额列，便于连读
					alignment = HorizontalAlignment.RIGHT;
				}
			}
			else if (profile == ColumnProfile.SUMMARY) {
				if (column == 1 || column == 2) {
					alignment = HorizontalAlignment.CENTER;
				}
				else if (column == 3) {
					alignment = HorizontalAlignment.RIGHT;
				}
			}
			else {
				if (column == 2 || column == 4 || column == 5) {
					alignment = HorizontalAlignment.CENTER;
				}
				else if (column == 6) {
					alignment = HorizontalAlignment.RIGHT;
				}
			}
			style.setAlignment(alignment);
		}

		private void applyNumberFormat(CellStyle style, Workbook workbook, int column) {
			String format = null;
			if (profile == ColumnProfile.DETAIL) {
				if (column == 10 || column == 12) {
					format = MONEY_FORMAT;
				}
				else if (column == 11) {
					format = QUANTITY_FORMAT;
				}
			}
			else if (profile == ColumnProfile.SUMMARY) {
				if (column == 1) {
					format = COUNT_FORMAT;
				}
				else if (column == 2) {
					format = QUANTITY_FORMAT;
				}
				else if (column == 3) {
					format = MONEY_FORMAT;
				}
			}
			else {
				if (column == 4) {
					format = COUNT_FORMAT;
				}
				else if (column == 5) {
					format = QUANTITY_FORMAT;
				}
				else if (column == 6) {
					format = MONEY_FORMAT;
				}
			}
			if (format != null) {
				style.setDataFormat(workbook.createDataFormat().getFormat(format));
			}
		}

		private Font font(Workbook workbook, boolean bold, IndexedColors color, short heightInPoints) {
			String key = bold + "|" + color.name() + "|" + heightInPoints;
			return fonts.computeIfAbsent(key, ignored -> {
				Font font = workbook.createFont();
				font.setFontName(FONT_NAME);
				font.setFontHeightInPoints(heightInPoints);
				font.setBold(bold);
				font.setColor(color.getIndex());
				return font;
			});
		}

	}

}
