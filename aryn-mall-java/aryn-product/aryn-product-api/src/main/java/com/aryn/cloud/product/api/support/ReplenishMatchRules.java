package com.aryn.cloud.product.api.support;

import java.math.BigDecimal;

/**
 * 补给单导入匹配的共享口径（纯函数，放在 api 层供商品域与订单域共用）。
 *
 * <p>为什么必须共享而不是各写一份：商品域负责「命中哪个 SKU」，
 * 订单域负责「这行算不算可导入」，两边都要判断规格等价；
 * 各写一份的结果是报告说匹配成功、确认时又被服务端拦下。
 *
 * <p>本类不依赖数据库、不抛业务异常，可脱离 Spring 单测。
 *
 * @author aryn
 * @since 2026/9/22
 */
public final class ReplenishMatchRules {

	/** 规格文本拼接分隔符（与 C 端展示口径一致） */
	public static final String SEPARATOR = "；";

	private ReplenishMatchRules() {
	}

	/**
	 * 规格规范化：去空白、全角括号/乘号转半角、统一小写。只用于**比较**，不用于展示。
	 */
	public static String normalizeSpec(String spec) {
		if (spec == null) {
			return "";
		}
		String text = spec.trim().toLowerCase();
		text = text.replace('（', '(').replace('）', ')')
			.replace('＊', '*').replace('×', '*');
		return text.replaceAll("[\\s\\u3000]+", "");
	}

	/** 商品名规范化：去空白，便于「品名 + 规格」等价比较 */
	public static String normalizeName(String name) {
		if (name == null) {
			return "";
		}
		return name.trim().toLowerCase().replaceAll("[\\s\\u3000]+", "");
	}

	/**
	 * 规格文本是否等价：任一侧为空视为**无法比较**（返回 true，不报规格变更），
	 * 因为线下清单常常不填规格；只有两边都有值且互相不包含时才判定变更。
	 */
	public static boolean specEquivalent(String excelSpec, String skuSpec) {
		String left = normalizeSpec(excelSpec);
		String right = normalizeSpec(skuSpec);
		if (left.isEmpty() || right.isEmpty()) {
			return true;
		}
		return left.equals(right) || left.contains(right) || right.contains(left);
	}

	/**
	 * 数量是否满足 MOQ 与步长（正整数前提下）。
	 */
	public static boolean quantityAcceptable(Integer quantity, Integer moq, Integer stepQty) {
		if (quantity == null || quantity <= 0) {
			return false;
		}
		int min = moq == null || moq < 1 ? 1 : moq;
		int step = stepQty == null || stepQty < 1 ? 1 : stepQty;
		return quantity >= min && quantity % step == 0;
	}

	/**
	 * 超库存时的建议调减量：库存向下取步长、不低于 MOQ；不可行时返回 null。
	 */
	public static Integer suggestedQuantity(Integer stock, Integer moq, Integer stepQty) {
		if (stock == null || stock <= 0) {
			return null;
		}
		int min = moq == null || moq < 1 ? 1 : moq;
		int step = stepQty == null || stepQty < 1 ? 1 : stepQty;
		int candidate = stock - (stock % step);
		if (candidate < min) {
			return null;
		}
		return candidate;
	}

	/**
	 * 把数量原文解析为整数：去掉 Excel 数值单元格残留的 .0，非法返回 null。
	 */
	public static Integer parseQuantity(Object value) {
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
		try {
			return new BigDecimal(text).intValueExact();
		}
		catch (ArithmeticException | NumberFormatException exception) {
			return null;
		}
	}

}
