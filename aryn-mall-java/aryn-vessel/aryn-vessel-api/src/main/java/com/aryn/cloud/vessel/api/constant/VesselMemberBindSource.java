package com.aryn.cloud.vessel.api.constant;

import java.util.List;

/**
 * 船舶成员绑定来源字典（唯一口径，对应 {@code vessel_member.bind_source}）。
 *
 * <p><b>为什么要把「怎么绑上的」落成一列</b>：{@code vessel_member} 是多个下游域的数据权限基线
 * （成员列表、靠港计划、内部配送单、共享购物车），而写入通道有 5 条、信任级别差异极大——
 * 从「运营审核」到「任何拿到分享链接的人」。此前所有写入都长一个样，
 * 运营看到一条成员关系无法判断它是审核通过、邀请码、还是分享链接自动补建的，
 * 误绑后也无从按来源筛出来批量回收。来源标记是「可追溯 + 可回收」的前提。
 *
 * <p>{@link #MANUAL} 只做历史数据兜底：DB 默认值，绝不表示某条业务路径。
 * 业务代码写入时必须显式传本类常量，不要依赖默认值。
 *
 * @author aryn
 * @since 2026/10/19
 */
public final class VesselMemberBindSource {

	/** 历史/未知（DB 默认值，仅兜底；业务代码不应主动写入） */
	public static final String MANUAL = "MANUAL";

	/** 绑定申请审核通过（申请船与目标船身份一致） */
	public static final String APPLY_AUDIT = "APPLY_AUDIT";

	/** 绑定申请审核通过，但申请船与目标船不一致（运营已确认 + 填写理由） */
	public static final String APPLY_AUDIT_MISMATCH = "APPLY_AUDIT_MISMATCH";

	/** 邀请码加入 */
	public static final String INVITE_CODE = "INVITE_CODE";

	/** 船内管理角色（C 端）添加成员 */
	public static final String MEMBER_ADD = "MEMBER_ADD";

	/** 管理端直接绑定成员 */
	public static final String ADMIN_ADD = "ADMIN_ADD";

	/** 分享购物车链接自动补建（信任级别最低，仅凭链接） */
	public static final String SHARE_CART = "SHARE_CART";

	/** 全部合法取值，按展示顺序 */
	public static final List<String> ALL = List.of(APPLY_AUDIT, APPLY_AUDIT_MISMATCH, INVITE_CODE, MEMBER_ADD,
			ADMIN_ADD, SHARE_CART, MANUAL);

	/** 取值 -> 中文名 */
	public static final List<Option> OPTIONS = List.of(new Option(APPLY_AUDIT, "申请审核"),
			new Option(APPLY_AUDIT_MISMATCH, "申请审核（船名不一致）"), new Option(INVITE_CODE, "邀请码"),
			new Option(MEMBER_ADD, "船内成员添加"), new Option(ADMIN_ADD, "管理端绑定"),
			new Option(SHARE_CART, "分享购物车自动加入"), new Option(MANUAL, "手工/历史"));

	private VesselMemberBindSource() {
	}

	/** 是否为合法来源码值 */
	public static boolean isValid(String value) {
		return value != null && ALL.contains(value);
	}

	/** 码值 -> 中文名；未知值回退到「手工/历史」，避免管理端出现空白标签 */
	public static String label(String value) {
		for (Option option : OPTIONS) {
			if (option.value().equals(value)) {
				return option.label();
			}
		}
		return "手工/历史";
	}

	/** 字典选项 */
	public record Option(String value, String label) {
	}

}
