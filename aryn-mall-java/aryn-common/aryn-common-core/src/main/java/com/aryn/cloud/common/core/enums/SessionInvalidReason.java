package com.aryn.cloud.common.core.enums;

import cn.hutool.core.util.StrUtil;

/**
 * 会话失效原因（401 响应体的机器可读契约）。
 *
 * <p>背景：Sa-Token 的 {@code NotLoginException#getType()} 已经能区分「被顶下线」「被踢下线」
 * 「自然过期」等场景（-1 ~ -7），但历史实现只把它们揉进中文 {@code msg} 返回，前端无法稳定区分，
 * 用户被顶下线时只能看到"登录已过期"甚至直接被踢回登录页。这里把原因固化成枚举，
 * 由 boot 过滤器 / cloud 网关过滤器 / MVC 兜底三个出口统一下发 {@code data.reason}。</p>
 *
 * <p>约束：</p>
 * <ul>
 *   <li>{@code code=401}、HTTP 200 的既有形态不变，只新增 {@code data} 字段（旧客户端无感）；</li>
 *   <li>{@link #name()} 即下发给前端的 wire 值，<b>不得改名</b>（改了会破坏前端 i18n 映射与契约测试）；</li>
 *   <li>{@link #getMessage()} 是脱敏后的固定中文文案：<b>不得包含 token / loginId 等凭证信息</b>；</li>
 *   <li>枚举位于 common-core（L0），boot 与 gateway 都可依赖，保证两侧口径同源。</li>
 * </ul>
 *
 * @see com.aryn.cloud.common.core.util.SessionInvalidPayload
 */
public enum SessionInvalidReason {

	/** 未能从请求中读取到有效 token */
	NOT_TOKEN("-1", "登录状态已失效，请重新登录"),

	/** token 无效（主动退出后的旧 token、映射丢失、未知场景等） */
	TOKEN_INVALID("-2", "登录状态已失效，请重新登录"),

	/** token 已过期（TOB 12h / TOC 30 天绝对有效期） */
	TOKEN_EXPIRED("-3", "登录已过期，请重新登录"),

	/** token 已被顶下线（同账号在另一处登录） */
	BE_REPLACED("-4", "账号已在其他设备登录"),

	/** token 已被踢下线（停用/改密/删除/管理员强退/租户停用等） */
	KICK_OUT("-5", "登录状态已失效，请重新登录"),

	/** token 已被冻结 */
	TOKEN_FREEZE("-6", "登录状态已冻结，请重新登录"),

	/** 未按照指定前缀提交 token */
	NO_PREFIX("-7", "登录状态已失效，请重新登录");

	/** Sa-Token {@code NotLoginException#getType()} 的取值 */
	private final String saTokenType;

	/** 下发给旧客户端/人类阅读的脱敏文案 */
	private final String message;

	SessionInvalidReason(String saTokenType, String message) {
		this.saTokenType = saTokenType;
		this.message = message;
	}

	public String getSaTokenType() {
		return saTokenType;
	}

	public String getMessage() {
		return message;
	}

	/**
	 * 根据 Sa-Token 未登录场景值解析原因；未知场景统一归为 {@link #TOKEN_INVALID}。
	 */
	public static SessionInvalidReason fromSaTokenType(String saTokenType) {
		if (StrUtil.isBlank(saTokenType)) {
			return TOKEN_INVALID;
		}
		for (SessionInvalidReason reason : values()) {
			if (reason.saTokenType.equals(saTokenType.trim())) {
				return reason;
			}
		}
		return TOKEN_INVALID;
	}

	/**
	 * 根据 wire 值（枚举名）解析原因；用于读取 Redis 失效记录时做防御性解析。
	 */
	public static SessionInvalidReason fromWire(String wire) {
		if (StrUtil.isBlank(wire)) {
			return TOKEN_INVALID;
		}
		for (SessionInvalidReason reason : values()) {
			if (reason.name().equalsIgnoreCase(wire.trim())) {
				return reason;
			}
		}
		return TOKEN_INVALID;
	}

}
