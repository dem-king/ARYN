package com.aryn.cloud.vessel.api.constant;

import java.util.List;

/**
 * 船舶类型字典（唯一口径）。
 *
 * <p>此前本字典散落在四处且口径不一致：实体注释与 DDL 注释是
 * 「1集装箱 2散货 3油轮 4杂货 5其他」，而 {@code VesselBindAuditDTO.newVesselType}
 * 的注释写成了「1货轮 2油轮 3集装箱 4散货 5其他」——同一个码值在审核链路上
 * 表达的是完全不同的船型，接入方按 DTO 注释传 {@code 3} 想表达「集装箱」，
 * 落库后管理端会显示「油轮」。
 *
 * <p>现在收敛到这一个常量类：实体注释、DDL 注释、管理端下拉、审核弹窗
 * 一律引用本类的取值，不再各处手写。新增类型只能改这里。
 *
 * @author aryn
 * @since 2026/10/18
 */
public final class VesselType {

	/** 集装箱船 */
	public static final String CONTAINER = "1";

	/** 散货船 */
	public static final String BULK = "2";

	/** 油轮 */
	public static final String TANKER = "3";

	/** 杂货船 */
	public static final String GENERAL_CARGO = "4";

	/** 其他（缺省值：审核建船未选类型时落这个，避免产出空船型） */
	public static final String OTHER = "5";

	/** 缺省船型 */
	public static final String DEFAULT = OTHER;

	/** 全部合法取值，按展示顺序 */
	public static final List<String> ALL = List.of(CONTAINER, BULK, TANKER, GENERAL_CARGO, OTHER);

	/** 取值 -> 中文名 */
	public static final List<Option> OPTIONS = List.of(
			new Option(CONTAINER, "集装箱"),
			new Option(BULK, "散货"),
			new Option(TANKER, "油轮"),
			new Option(GENERAL_CARGO, "杂货"),
			new Option(OTHER, "其他"));

	private VesselType() {
	}

	/** 是否为合法船型码值 */
	public static boolean isValid(String value) {
		return value != null && ALL.contains(value);
	}

	/** 码值 -> 中文名；未知值回退到「其他」，避免前端出现空白下拉 */
	public static String label(String value) {
		for (Option option : OPTIONS) {
			if (option.value().equals(value)) {
				return option.label();
			}
		}
		return "其他";
	}

	/** 字典选项 */
	public record Option(String value, String label) {
	}

}
