package com.aryn.cloud.common.core.util;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 401 会话失效响应体中 {@code data} 的组装器（boot 过滤器 / cloud 网关过滤器 / MVC 兜底共用）。
 *
 * <p>响应形态（HTTP 保持 200，沿用 body.code 约定）：</p>
 * <pre>
 * {
 *   "code": 401,
 *   "msg": "登录状态已失效，请重新登录",
 *   "data": {
 *     "reason": "KICK_OUT",
 *     "subReason": "ACCOUNT_DISABLED",
 *     "invalidAt": "2026-10-28T23:50:12+08:00",
 *     "device": { "os": "Windows 10", "browser": "Chrome 120", "location": "上海市", "ip": "1.2.3.4", "loginTime": "..." }
 *   }
 * }
 * </pre>
 *
 * <p>安全约束：{@code device} 只放白名单字段，且调用方传入的 Map 里即使带了
 * {@code ipAddr}/{@code tokenId}/{@code tenantId} 等字段也会被丢弃；响应体任何位置都不得出现
 * token、loginId、手机号。2026-11-01 按产品决策放开「完整 IP」：被顶下线的新设备信息允许携带
 * {@code ip} 字段（用户自己识别新登录设备），但仅限 device 白名单内，其它位置仍不得出现 IP。</p>
 *
 * <p>兼容约束：{@code subReason} 只在 {@code reason=KICK_OUT} 时输出（其它原因即使传入也忽略），
 * 且为可空增量字段；旧客户端忽略。</p>
 */
public final class SessionInvalidPayload {

	/** 失效原因 wire 值，取值见 {@link SessionInvalidReason#name()} */
	public static final String REASON = "reason";

	/** 被踢下线的细分原因 wire 值，取值见 {@link SessionInvalidSubReason#name()}；仅 KICK_OUT 时出现 */
	public static final String SUB_REASON = "subReason";

	/** 失效发生时间（ISO8601） */
	public static final String INVALID_AT = "invalidAt";

	/** 新设备信息（仅被顶下线且有记录时返回） */
	public static final String DEVICE = "device";

	public static final String DEVICE_OS = "os";

	public static final String DEVICE_BROWSER = "browser";

	public static final String DEVICE_LOCATION = "location";

	/** 新登录设备的 IP（仅被顶下线且有记录时返回；2026-11-01 产品决策放开） */
	public static final String DEVICE_IP = "ip";

	public static final String DEVICE_LOGIN_TIME = "loginTime";

	/** 设备字段展示长度上限，防止恶意超长 UA 撑爆响应体 */
	private static final int DEVICE_FIELD_MAX_LENGTH = 40;

	private SessionInvalidPayload() {
	}

	/**
	 * 组装 data 结构（一期签名，无 subReason）。
	 */
	public static Map<String, Object> of(SessionInvalidReason reason, String invalidAt, Map<String, String> device) {
		return of(reason, null, invalidAt, device);
	}

	/**
	 * 组装 data 结构（二期：支持 subReason）。空值字段直接省略，前端按"缺失即隐藏/降级"渲染。
	 * @param reason 失效原因，空时按 {@link SessionInvalidReason#TOKEN_INVALID} 兜底
	 * @param subReason 细分原因；仅当 reason 为 KICK_OUT 时输出，其它原因忽略
	 * @param invalidAt 失效时间（ISO8601），可空
	 * @param device 设备信息，只取白名单字段，可空
	 */
	public static Map<String, Object> of(SessionInvalidReason reason, SessionInvalidSubReason subReason,
			String invalidAt, Map<String, String> device) {
		SessionInvalidReason resolved = reason == null ? SessionInvalidReason.TOKEN_INVALID : reason;
		Map<String, Object> data = new LinkedHashMap<>(5);
		data.put(REASON, resolved.name());
		if (resolved == SessionInvalidReason.KICK_OUT && subReason != null) {
			data.put(SUB_REASON, subReason.name());
		}
		if (StrUtil.isNotBlank(invalidAt)) {
			data.put(INVALID_AT, invalidAt);
		}
		Map<String, String> cleaned = cleanDevice(device);
		if (!cleaned.isEmpty()) {
			data.put(DEVICE, cleaned);
		}
		return data;
	}

	/**
	 * 只保留白名单设备字段并做长度截断；全空时返回空 Map。
	 */
	public static Map<String, String> cleanDevice(Map<String, String> device) {
		Map<String, String> cleaned = new LinkedHashMap<>(5);
		if (device == null || device.isEmpty()) {
			return cleaned;
		}
		putIfPresent(cleaned, DEVICE_OS, device.get(DEVICE_OS));
		putIfPresent(cleaned, DEVICE_BROWSER, device.get(DEVICE_BROWSER));
		putIfPresent(cleaned, DEVICE_LOCATION, device.get(DEVICE_LOCATION));
		putIfPresent(cleaned, DEVICE_IP, device.get(DEVICE_IP));
		putIfPresent(cleaned, DEVICE_LOGIN_TIME, device.get(DEVICE_LOGIN_TIME));
		return cleaned;
	}

	private static void putIfPresent(Map<String, String> target, String key, String value) {
		if (StrUtil.isBlank(value)) {
			return;
		}
		String trimmed = value.trim();
		if (trimmed.length() > DEVICE_FIELD_MAX_LENGTH) {
			trimmed = trimmed.substring(0, DEVICE_FIELD_MAX_LENGTH);
		}
		target.put(key, trimmed);
	}

}
