package com.aryn.cloud.vessel.api.constant;

import java.util.List;

/**
 * 船舶成员角色字典 + <b>可分配白名单</b>。
 *
 * <p>{@link com.aryn.cloud.vessel.api.entity.VesselMember} 里既有的 {@code ROLE_*} 常量保留不动
 * （避免大面积改动），取值与本类完全一致；新代码统一引用本类。
 *
 * <p><b>为什么必须有 ASSIGNABLE</b>：成员角色此前由各入口直接从请求体取——
 * 审核 DTO 传 {@code "1"} 就能把申请人写成「发起人」（船长授权，可移除成员、管成员），
 * 管理端绑定接口传任意值也会原样落库。发起人是船舶的最高管理角色，只能由建船/认领流程产生，
 * 任何「加人/审核」通道都不该授予。因此所有写入通道只允许
 * {@link #CREW} / {@link #CONFIRMER} / {@link #SALES} 三个值。
 *
 * <p>白名单最终在 {@code VesselMemberRegistrar} 内兜底（5 条写入通道都过它），
 * 业务入口再加一层以便给出更贴近场景的报错。
 *
 * @author aryn
 * @since 2026/10/19
 */
public final class VesselMemberRole {

	/** 发起人 / 船长授权：只能由建船、认领流程产生，任何加人通道不可授予 */
	public static final String OWNER = "1";

	/** 普通船员 */
	public static final String CREW = "2";

	/** 采购确认人 */
	public static final String CONFIRMER = "3";

	/** 业务员（公司销售，可添加成员、可代船员下单） */
	public static final String SALES = "4";

	/**
	 * 任何写入通道允许分配的角色。
	 *
	 * <p>刻意不含 {@link #OWNER}：一条船只能有一个发起人，且发起人关系到「谁是这条船的责任人」，
	 * 不能通过「拉人」「审核申请」这类批量、可重复的通道产生。
	 */
	public static final List<String> ASSIGNABLE = List.of(CREW, CONFIRMER, SALES);

	/** 取值 -> 中文名 */
	public static final List<Option> OPTIONS = List.of(new Option(OWNER, "发起人"), new Option(CREW, "普通船员"),
			new Option(CONFIRMER, "采购确认人"), new Option(SALES, "商城业务员"));

	private VesselMemberRole() {
	}

	/** 是否为可分配角色（非 null 且属于 {@link #ASSIGNABLE}） */
	public static boolean isAssignable(String role) {
		return role != null && ASSIGNABLE.contains(role);
	}

	/** 码值 -> 中文名；未知值回退到「成员」，避免前端出现空白角色 */
	public static String label(String role) {
		for (Option option : OPTIONS) {
			if (option.value().equals(role)) {
				return option.label();
			}
		}
		return "成员";
	}

	/** 字典选项 */
	public record Option(String value, String label) {
	}

}
