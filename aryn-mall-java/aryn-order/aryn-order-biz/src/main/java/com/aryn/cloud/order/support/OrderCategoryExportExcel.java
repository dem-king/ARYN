package com.aryn.cloud.order.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理端订单导出（按分类分组）。
 *
 * <p>两个工作表：
 * <ul>
 *   <li><b>订单明细</b>：一行一个商品，订单维度列逐行重复（便于筛选/透视）；
 *       订单内商品按分类分组排列，每个分类块尾部跟一行「小计」，订单末尾跟一行「订单合计」。</li>
 *   <li><b>分类汇总</b>：跨订单按分类聚合的件数/金额，供按分类备货、装箱用。</li>
 * </ul>
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

	private static final List<String> DETAIL_HEAD = List.of(
		"订单号", "下单时间", "购买场景", "配送方式", "收货人", "收货人电话", "收货地址",
		"分类", "商品名称", "规格", "单价（元）", "数量", "金额（元）", "订购人", "备注");

	private static final List<String> SUMMARY_HEAD = List.of("分类", "商品种数", "总数量", "总金额（元）");

	private OrderCategoryExportExcel() {
	}

	/**
	 * 写出导出 Excel
	 * @param orders 订单列表（商品行需已回填分类名）
	 * @param outputStream 输出流（由调用方负责关闭）
	 */
	public static void write(List<OrderInfo> orders, OutputStream outputStream) {
		try (ExcelWriter excelWriter = EasyExcel.write(outputStream).build()) {
			WriteSheet detailSheet = EasyExcel.writerSheet(0, "订单明细").head(detailHead()).build();
			excelWriter.write(detailRows(orders), detailSheet);

			WriteSheet summarySheet = EasyExcel.writerSheet(1, "分类汇总").head(summaryHead()).build();
			excelWriter.write(summaryRows(orders), summarySheet);
		}
	}

	private static List<List<String>> detailHead() {
		return DETAIL_HEAD.stream().map(List::of).toList();
	}

	private static List<List<String>> summaryHead() {
		return SUMMARY_HEAD.stream().map(List::of).toList();
	}

	private static List<List<Object>> detailRows(List<OrderInfo> orders) {
		List<List<Object>> rows = new ArrayList<>();
		for (OrderInfo order : orders) {
			List<OrderItemEntity> items = order.getOrderItemList() == null
					? List.of() : order.getOrderItemList();
			Map<String, List<OrderItemEntity>> byCategory = groupByCategory(items);

			for (Map.Entry<String, List<OrderItemEntity>> entry : byCategory.entrySet()) {
				int categoryQuantity = 0;
				BigDecimal categoryAmount = BigDecimal.ZERO;
				for (OrderItemEntity item : entry.getValue()) {
					categoryQuantity = categoryQuantity + nullSafeQuantity(item);
					categoryAmount = categoryAmount.add(nullSafeTotalPrice(item));
					rows.add(detailRow(order, entry.getKey(), item));
				}
				rows.add(subtotalRow(order, entry.getKey(), categoryQuantity, categoryAmount));
			}
			rows.add(orderTotalRow(order, items));
		}
		return rows;
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

	private static List<Object> subtotalRow(OrderInfo order, String categoryName,
			int quantity, BigDecimal amount) {
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
		java.text.Collator collator = java.text.Collator.getInstance(java.util.Locale.CHINA);
		grouped.entrySet()
			.stream()
			.sorted(Comparator
				.comparing((Map.Entry<String, List<OrderItemEntity>> entry) -> entry.getKey()
					.equals(UNCATEGORIZED))
				.thenComparing(Map.Entry::getKey, collator))
			.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
		return sorted;
	}

	/** 跨订单按分类聚合：分类 → 商品种数（去重 spuId）/ 总件数 / 总金额 */
	private static List<List<Object>> summaryRows(List<OrderInfo> orders) {
		Map<String, Integer> quantityByCategory = new LinkedHashMap<>();
		Map<String, BigDecimal> amountByCategory = new LinkedHashMap<>();
		Map<String, Set<String>> spuIdsByCategory = new LinkedHashMap<>();
		for (OrderInfo order : orders) {
			List<OrderItemEntity> items = order.getOrderItemList() == null
					? List.of() : order.getOrderItemList();
			for (OrderItemEntity item : items) {
				String category = categoryOf(item);
				quantityByCategory.merge(category, nullSafeQuantity(item), Integer::sum);
				amountByCategory.merge(category, nullSafeTotalPrice(item), BigDecimal::add);
				spuIdsByCategory.computeIfAbsent(category, key -> new LinkedHashSet<>())
					.add(item.getSpuId());
			}
		}
		List<List<Object>> rows = new ArrayList<>();
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
			});
		return rows;
	}

	private static String categoryOf(OrderItemEntity item) {
		return item.getCategoryName() == null || item.getCategoryName().isBlank()
				? UNCATEGORIZED : item.getCategoryName();
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

	/** 购买场景：1 海员个人购买 / 2 船供采购（order_item.purchase_scene 同口径） */
	private static String sceneText(String scene) {
		if ("1".equals(scene)) {
			return "个人购买";
		}
		if ("2".equals(scene)) {
			return "船供采购";
		}
		return "";
	}

	/** 配送方式：与 delivery_way 字典对齐（1 普通快递 / 2 上门自提 / 4 公司港口/船舶内部配送） */
	private static String deliveryWayText(String way) {
		if ("1".equals(way)) {
			return "普通快递";
		}
		if ("2".equals(way)) {
			return "上门自提";
		}
		if ("4".equals(way)) {
			return "公司港口/船舶内部配送";
		}
		return way == null ? "" : way;
	}

	private static String formatTime(LocalDateTime time) {
		return time == null ? "" : TIME_FORMATTER.format(time);
	}

}
