package com.aryn.cloud.promotion.util;

import java.util.Locale;

/**
 * 主题颜色小工具：#RRGGBB 解析 / 向白色混合 / 回写。
 * 仅供装修主题的辅色衍生使用，不承担任意色彩空间的通用换算。
 *
 * @author 雨滴kian
 * @date 2026/10/02
 */
public final class ThemeColorUtils {

	private ThemeColorUtils() {
	}

	/**
	 * 由主色向白色混合衍生辅色；主色缺失或非法时返回 null，调用方自行决定兜底。
	 */
	public static String deriveSecondary(String primaryColor) {
		int[] rgb = parseRgb(primaryColor);
		if (rgb == null) {
			return null;
		}
		return formatRgb(mixTowardWhite(rgb, 0.35D));
	}

	private static int[] parseRgb(String color) {
		if (color == null || !color.matches("^#[0-9a-fA-F]{6}$")) {
			return null;
		}
		return new int[] { Integer.parseInt(color.substring(1, 3), 16), Integer.parseInt(color.substring(3, 5), 16),
				Integer.parseInt(color.substring(5, 7), 16) };
	}

	private static int[] mixTowardWhite(int[] rgb, double ratio) {
		return new int[] { (int) Math.round(rgb[0] + (255 - rgb[0]) * ratio),
				(int) Math.round(rgb[1] + (255 - rgb[1]) * ratio),
				(int) Math.round(rgb[2] + (255 - rgb[2]) * ratio) };
	}

	private static String formatRgb(int[] rgb) {
		return String.format(Locale.ROOT, "#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
	}

}
