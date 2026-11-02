package com.aryn.cloud.common.core.util;

import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 401 会话失效响应体的最终序列化（boot 过滤器 / cloud 网关过滤器共用）。
 *
 * <p><b>为什么不能直接返回 {@code SaResult}：</b>Sa-Token 的 {@code SaServletFilter} /
 * {@code SaReactorFilter} 用 {@code String.valueOf(errorHandler.run(e))} 写响应，而
 * {@code SaResult#toString()} 对 {@code data} 只做 {@code String.valueOf(data)}——
 * 嵌套 Map 会被序列化成 Java 的 {@code {reason=NOT_TOKEN}}（键无引号，**不是合法 JSON**）。
 * 2026-10-29 boot 部署验证时实际复现：浏览器 Axios 无法 parse，前端拿到不到 reason。
 *
 * <p>因此这里显式把「code/msg/data」序列化为 JSON 字符串返回；过滤器 {@code String.valueOf}
 * 对 String 原样输出，响应体即合法 JSON。MVC 兜底 handler 走 Spring Jackson，不受此影响。</p>
 */
public final class SessionInvalidResponse {

	/** 建议的 Content-Type（Sa-Token 过滤器默认写 text/plain，Axios 仍会尝试 JSON.parse） */
	public static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";

	private SessionInvalidResponse() {
	}

	/**
	 * 构建 401 响应体 JSON。
	 * @param reason 失效原因（决定 msg 文案）
	 * @param data {@link SessionInvalidPayload} 组装出的 data 结构；为空时兜底为仅含 reason
	 */
	public static String toJson(SessionInvalidReason reason, Map<String, Object> data) {
		SessionInvalidReason resolved = reason == null ? SessionInvalidReason.TOKEN_INVALID : reason;
		Map<String, Object> body = new LinkedHashMap<>(3);
		body.put("code", 401);
		body.put("msg", resolved.getMessage());
		body.put("data", data == null ? SessionInvalidPayload.of(resolved, null, null) : data);
		return JSONUtil.toJsonStr(body);
	}

}
