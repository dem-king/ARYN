package com.aryn.cloud.common.core.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 被踢下线的细分原因（401 响应体 {@code data.subReason} 的机器可读契约，二期）。
 *
 * <p>只有 {@link SessionInvalidReason#KICK_OUT} 会携带 subReason：同为"被踢下线"，
 * 「管理员强退」「账号停用」「改密」「删除」「租户停用」「配送资格撤销」「会员禁用」
 * 对用户的含义与下一步动作完全不同（有的可重登、有的重登也进不去）。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>{@link #name()} 即下发给前端的 wire 值，<b>不得改名</b>（前端按此映射文案与按钮）；</li>
 *   <li>未知值前端按"无 subReason"降级，后端 {@link #fromWire} 对未知值返回 {@code null}；</li>
 *   <li>字段可空，旧客户端忽略。</li>
 * </ul>
 */
public enum SessionInvalidSubReason {

	/** 管理员在「在线用户」页强制下线 */
	ADMIN_KICK("管理员强制下线"),

	/** 员工账号被停用 */
	ACCOUNT_DISABLED("账号停用"),

	/** 密码被修改 */
	PASSWORD_CHANGED("密码修改"),

	/** 账号被删除（员工或 C 端会员） */
	ACCOUNT_DELETED("账号删除"),

	/** 租户停用/到期/套餐变更/删除/平台强制下线 */
	TENANT_DISABLED("租户停用"),

	/** 配送资格撤销（删除配送员/解绑商城账号/停用配送资格） */
	DELIVERY_REVOKED("配送资格撤销"),

	/** C 端会员被禁用 */
	MEMBER_DISABLED("会员禁用");

	private final String label;

	SessionInvalidSubReason(String label) {
		this.label = label;
	}

	/** 仅用于服务端日志，不下发给前端（前端文案由 i18n 决定） */
	public String getLabel() {
		return label;
	}

	/**
	 * 根据 wire 值解析细分原因；未知/空值返回 {@code null}（前端按无 subReason 降级）。
	 */
	public static SessionInvalidSubReason fromWire(String wire) {
		if (StrUtil.isBlank(wire)) {
			return null;
		}
		for (SessionInvalidSubReason subReason : values()) {
			if (subReason.name().equalsIgnoreCase(wire.trim())) {
				return subReason;
			}
		}
		return null;
	}

}
