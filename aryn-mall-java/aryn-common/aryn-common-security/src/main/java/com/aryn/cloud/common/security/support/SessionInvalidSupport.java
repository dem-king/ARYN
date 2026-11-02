package com.aryn.cloud.common.security.support;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.SessionInvalidPushConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.core.util.SessionInvalidPayload;
import com.aryn.cloud.common.core.util.SpringUtils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话失效原因 / 被顶下线新设备信息的读写工具（boot/cloud 业务侧共用）。
 *
 * <p>为什么需要它：Sa-Token 的 401 响应只带中文 {@code msg}，前端无法区分「被顶下线」「被踢下线」
 * 「自然过期」；而被顶下线时用户最想知道「在哪台设备登录的」。这里在旧 token 被顶掉的瞬间
 * 把「原因 + 新设备信息」写进 Redis（token 维度），三个 401 出口按 token 反查后随响应下发。</p>
 *
 * <p>三期增加<b>主动推送</b>：写完失效记录后，按账号身份（tenantId + 接收者类型 + userId）向
 * {@link SessionInvalidPushConstants#CHANNEL} 发布 {@code SESSION_INVALID} 事件，由 message-biz
 * 投递到旧设备已建立的 WebSocket，实现秒级弹窗；推送失败只告警，前端仍由下一次 HTTP 401 兜底。</p>
 *
 * <p>写入时机：登录链路（{@code SecurityUtils#loginByDevice}）在 {@code StpUtil.login} 成功后调用
 * {@link #markReplaced}。此时 {@code ArynSaTokenListener#doLogin} 刚把新会话的在线记录
 * （browser / os / location）写进 Redis，直接复用可避免再解析一次 UA，更避免二次调用外部 IP 库
 * （{@code IpUtils#getWhoisAddress} 是 HTTP 调用）。</p>
 *
 * <p>可靠性：所有 Redis / Bean 查找操作都吞掉异常只记 warn —— 这是登录与 401 出口上的旁路能力，
 * 任何失败都不能影响主流程；读不到记录时前端按通用文案降级。</p>
 *
 * <p>不使用 Spring Bean：common-security 的 Bean 同时通过组件扫描（boot）与
 * {@code AutoConfiguration.imports}（cloud 各服务）注册，按类型注入会出现重复 Bean 风险；
 * 这里沿用仓库既有的 {@code SpringUtils#getBean} 静态取 Bean 模式（同 {@code WxMiniAppConfiguration}）。</p>
 */
@Slf4j
@UtilityClass
public class SessionInvalidSupport {

	/** 失效记录 TTL：覆盖"旧设备隔天才回来操作"的窗口，成本可忽略 */
	public static final long TTL_SECONDS = 7L * 24 * 3600;

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	/**
	 * 记录"被顶下线"原因：为每个被顶掉的旧 token 写一份失效记录（含新设备信息），并主动推送。
	 *
	 * @param replacedTokens 登录前该账号该设备类型下的旧 token 列表
	 * @param newTokenValue 本次登录签发的新 token（用于读取新会话的在线记录，可空）
	 * @param loginId 账号 id（推送路由用）
	 * @param tenantId 租户 id（推送路由用，空则只写记录不推送）
	 * @param deviceType 设备类型（TOB/TOC，推送接收者类型由此推导）
	 */
	public static void markReplaced(List<String> replacedTokens, String newTokenValue, Object loginId,
			String tenantId, String deviceType) {
		markReplaced(replacedTokens, newTokenValue, loginId, tenantId, deviceType, redisTemplateOrNull());
	}

	static void markReplaced(List<String> replacedTokens, String newTokenValue, Object loginId, String tenantId,
			String deviceType, StringRedisTemplate redisTemplate) {
		if (redisTemplate == null || CollUtil.isEmpty(replacedTokens)) {
			return;
		}
		String now = nowIso();
		Map<String, String> device = readLoginDevice(redisTemplate, newTokenValue, now);
		String json = JSONUtil.toJsonStr(SessionInvalidPayload.of(SessionInvalidReason.BE_REPLACED, now, device));
		for (String replacedToken : replacedTokens) {
			if (StrUtil.isBlank(replacedToken)) {
				continue;
			}
			try {
				redisTemplate.opsForValue()
					.set(CacheConstants.SESSION_INVALID_KEY + replacedToken.trim(), json, TTL_SECONDS, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				// token 是 bearer 凭证，日志不得输出 token 值本身
				log.warn("写入会话失效原因失败，已跳过该会话: {}", e.getMessage());
			}
		}
		publishSessionInvalid(redisTemplate, SessionInvalidReason.BE_REPLACED, null, now, device, tenantId,
				recipientTypeOf(deviceType), loginId == null ? null : String.valueOf(loginId));
	}

	/**
	 * 记录"被踢下线"的细分原因：为被踢掉的每个 token 写一份失效记录（无设备信息），并主动推送。
	 *
	 * <p><b>必须在 kickout 之前调用</b>：kickout 一执行 token→loginId 映射就被删除，
	 * 之后再写也无法按 token 反查；推送也需要在连接仍存在时发出。写入/推送失败只告警，不影响踢人。</p>
	 *
	 * @param tokens 即将被踢掉的 token 列表
	 * @param subReason 细分原因（管理员强退/停用/改密/删除/租户停用/配送撤销/会员禁用）
	 */
	public static void markKickoutTokens(List<String> tokens, SessionInvalidSubReason subReason) {
		markKickoutTokens(tokens, subReason, redisTemplateOrNull());
	}

	static void markKickoutTokens(List<String> tokens, SessionInvalidSubReason subReason,
			StringRedisTemplate redisTemplate) {
		if (redisTemplate == null || subReason == null || CollUtil.isEmpty(tokens)) {
			return;
		}
		String now = nowIso();
		String json = JSONUtil.toJsonStr(
				SessionInvalidPayload.of(SessionInvalidReason.KICK_OUT, subReason, now, null));
		for (String token : tokens) {
			if (StrUtil.isBlank(token)) {
				continue;
			}
			String trimmed = token.trim();
			// 必须在 kickout 之前解析身份/在线记录：kickout 后 token→loginId 映射与在线记录都会消失
			PushTarget pushTarget = resolvePushTarget(redisTemplate, trimmed);
			try {
				redisTemplate.opsForValue()
					.set(CacheConstants.SESSION_INVALID_KEY + trimmed, json, TTL_SECONDS, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				// token 是 bearer 凭证，日志不得输出 token 值本身
				log.warn("写入踢下线原因失败，已跳过该会话: {}", e.getMessage());
			}
			if (pushTarget != null) {
				publishSessionInvalid(redisTemplate, SessionInvalidReason.KICK_OUT, subReason, now, null,
						pushTarget.tenantId(), pushTarget.recipientType(), pushTarget.recipientId());
			}
		}
	}

	/**
	 * 带原因的强制下线（账号 + 设备类型，会顶掉该端全部会话）。
	 *
	 * <p>先枚举该账号该端的 token 写失效记录并推送，再执行 Sa-Token {@code kickout}；
	 * 记录/推送失败不影响踢人，调用方原有的异常处理语义不变。</p>
	 */
	public static void kickoutWithReason(Object loginId, String deviceType, SessionInvalidSubReason subReason) {
		if (loginId != null && StrUtil.isNotBlank(deviceType) && subReason != null) {
			try {
				markKickoutTokens(StpUtil.getTokenValueListByLoginId(loginId, deviceType), subReason);
			}
			catch (Exception e) {
				log.warn("踢人前记录会话失效原因失败，按无原因降级: {}", e.getMessage());
			}
		}
		StpUtil.kickout(loginId, deviceType);
	}

	/**
	 * 带原因的单 token 强制下线（管理员在「在线用户」页强退）。
	 */
	public static void kickoutTokenWithReason(String tokenValue, SessionInvalidSubReason subReason) {
		if (StrUtil.isNotBlank(tokenValue) && subReason != null) {
			try {
				markKickoutTokens(List.of(tokenValue), subReason);
			}
			catch (Exception e) {
				log.warn("踢人前记录会话失效原因失败，按无原因降级: {}", e.getMessage());
			}
		}
		StpUtil.kickoutByTokenValue(tokenValue);
	}

	/**
	 * 向消息推送通道发布 {@code SESSION_INVALID} 事件（尽力而为）。
	 *
	 * <p>路由键 = {@code tenantId + recipientType + recipientId}，与 message-biz 的 WS 会话
	 * 身份键一致；{@code originInstanceId} 用随机值，确保所有 message 实例都尝试投递
	 * （只有持有该会话连接的实例会真正发送）。事件中<b>不包含 token / loginId 原始值</b>。</p>
	 */
	public static void publishSessionInvalid(SessionInvalidReason reason, SessionInvalidSubReason subReason,
			String invalidAt, Map<String, String> device, String tenantId, String recipientType,
			String recipientId) {
		publishSessionInvalid(redisTemplateOrNull(), reason, subReason, invalidAt, device, tenantId,
				recipientType, recipientId);
	}

	static void publishSessionInvalid(StringRedisTemplate redisTemplate, SessionInvalidReason reason,
			SessionInvalidSubReason subReason, String invalidAt, Map<String, String> device, String tenantId,
			String recipientType, String recipientId) {
		if (redisTemplate == null || StrUtil.isBlank(tenantId) || StrUtil.isBlank(recipientType)
				|| StrUtil.isBlank(recipientId)) {
			return;
		}
		try {
			Map<String, Object> event = new LinkedHashMap<>();
			event.put("eventType", SessionInvalidPushConstants.EVENT_TYPE_SESSION_INVALID);
			event.put("tenantId", tenantId);
			event.put("recipientType", recipientType);
			event.put("recipientId", recipientId);
			event.put("reason",
					(reason == null ? SessionInvalidReason.TOKEN_INVALID : reason).name());
			if (reason == SessionInvalidReason.KICK_OUT && subReason != null) {
				event.put("subReason", subReason.name());
			}
			if (StrUtil.isNotBlank(invalidAt)) {
				event.put("invalidAt", invalidAt);
			}
			Map<String, String> cleanedDevice = SessionInvalidPayload.cleanDevice(device);
			if (!cleanedDevice.isEmpty()) {
				event.put("device", cleanedDevice);
			}
			event.put("originInstanceId", UUID.randomUUID().toString());
			redisTemplate.convertAndSend(SessionInvalidPushConstants.CHANNEL, JSONUtil.toJsonStr(event));
		}
		catch (Exception e) {
			log.warn("会话失效 WS 推送失败，前端将由下一次 HTTP 401 兜底: {}", e.getMessage());
		}
	}

	/**
	 * 组装 401 响应体的 {@code data}：原因 + 失效时间 + 被顶下线的新设备信息。
	 *
	 * @param reason 由异常场景值解析出的原因（权威值）
	 */
	public static Map<String, Object> resolveData(SessionInvalidReason reason) {
		return resolveData(reason, redisTemplateOrNull(), currentToken());
	}

	static Map<String, Object> resolveData(SessionInvalidReason reason, StringRedisTemplate redisTemplate,
			String tokenValue) {
		SessionInvalidReason resolved = reason == null ? SessionInvalidReason.TOKEN_INVALID : reason;
		if (redisTemplate == null || StrUtil.isBlank(tokenValue)) {
			return SessionInvalidPayload.of(resolved, null, null);
		}
		try {
			String json = redisTemplate.opsForValue().get(CacheConstants.SESSION_INVALID_KEY + tokenValue.trim());
			if (StrUtil.isBlank(json)) {
				return SessionInvalidPayload.of(resolved, null, null);
			}
			JSONObject record = JSONUtil.parseObj(json);
			return SessionInvalidPayload.of(resolved,
					SessionInvalidSubReason.fromWire(record.getStr(SessionInvalidPayload.SUB_REASON)),
					record.getStr(SessionInvalidPayload.INVALID_AT),
					deviceOf(record.getJSONObject(SessionInvalidPayload.DEVICE)));
		}
		catch (Exception e) {
			log.warn("读取会话失效原因失败，按无原因降级: {}", e.getMessage());
			return SessionInvalidPayload.of(resolved, null, null);
		}
	}

	/**
	 * 当前请求携带的原始 token；取不到返回 {@code null}。
	 *
	 * <p>优先走 Sa-Token 自己的读取逻辑（与服务端校验口径一致），失败再退回请求头；
	 * 任何异常都吞掉——401 出口不能因为"取不到 token"而恶化。</p>
	 */
	public static String currentToken() {
		try {
			String tokenValue = StpUtil.getTokenValue();
			if (StrUtil.isNotBlank(tokenValue)) {
				return tokenValue;
			}
		}
		catch (Exception ignored) {
			// 非 Web 上下文或 Sa-Token 上下文异常时走请求头兜底
		}
		try {
			return SaHolder.getRequest().getHeader(SaManager.getConfig().getTokenName());
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * 当前时间（ISO8601，带服务端时区偏移），供失效时间/登录时间使用。
	 */
	public static String nowIso() {
		return ZonedDateTime.now().format(ISO_FORMATTER);
	}

	/**
	 * 设备类型 → 推送接收者类型（TOB→SYS_USER，TOC→MALL_USER）。
	 */
	public static String recipientTypeOf(String deviceType) {
		if (StrUtil.isBlank(deviceType)) {
			return null;
		}
		if (DeviceTypeEnum.TOB.getDevice().equalsIgnoreCase(deviceType.trim())) {
			return SessionInvalidPushConstants.RECIPIENT_TYPE_SYS_USER;
		}
		if (DeviceTypeEnum.TOC.getDevice().equalsIgnoreCase(deviceType.trim())) {
			return SessionInvalidPushConstants.RECIPIENT_TYPE_MALL_USER;
		}
		return null;
	}

	/**
	 * 解析被踢 token 的推送路由信息（loginId + 租户 + 设备类型）；解析不到返回 null（只写记录不推送）。
	 */
	private static PushTarget resolvePushTarget(StringRedisTemplate redisTemplate, String tokenValue) {
		try {
			Object loginId = StpUtil.getLoginIdByToken(tokenValue);
			if (loginId == null) {
				return null;
			}
			JSONObject online = readOnlineRecord(redisTemplate, tokenValue);
			if (online == null) {
				return null;
			}
			String tenantId = online.getStr("tenantId");
			String recipientType = recipientTypeOf(online.getStr("deviceType"));
			if (StrUtil.isBlank(tenantId) || StrUtil.isBlank(recipientType)) {
				return null;
			}
			return new PushTarget(String.valueOf(loginId), tenantId, recipientType);
		}
		catch (Exception e) {
			// 推送是旁路增强，解析失败不告警刷屏
			return null;
		}
	}

	/**
	 * 从新会话的在线记录（{@code ArynSaTokenListener#doLogin} 写入）中提取设备信息。
	 * 记录缺失时返回 {@code null}，前端会自动隐藏设备行。
	 *
	 * <p>UA 解析不出来的客户端在线记录里是 {@code Unknown}（hutool 的兜底值），对用户没有
	 * 信息量，按缺失处理；IP 从在线记录的 {@code ipAddr} 透传为白名单字段 {@code ip}
	 * （2026-11-01 产品决策：被顶下线弹窗展示新登录设备的 IP）。</p>
	 */
	private static Map<String, String> readLoginDevice(StringRedisTemplate redisTemplate, String newTokenValue,
			String loginTime) {
		JSONObject record = readOnlineRecord(redisTemplate, newTokenValue);
		if (record == null) {
			return null;
		}
		Map<String, String> device = new LinkedHashMap<>(5);
		device.put(SessionInvalidPayload.DEVICE_OS, meaningful(record.getStr("os")));
		device.put(SessionInvalidPayload.DEVICE_BROWSER, meaningful(record.getStr("browser")));
		device.put(SessionInvalidPayload.DEVICE_LOCATION, StrUtil.trimToEmpty(record.getStr("location")));
		device.put(SessionInvalidPayload.DEVICE_IP, StrUtil.trimToEmpty(record.getStr("ipAddr")));
		device.put(SessionInvalidPayload.DEVICE_LOGIN_TIME, loginTime);
		return SessionInvalidPayload.cleanDevice(device).isEmpty() ? null : device;
	}

	/** 去空白后按有效值返回；UA 解析兜底值 Unknown 对用户无信息量，按缺失处理。 */
	private static String meaningful(String value) {
		String trimmed = StrUtil.trimToEmpty(value);
		return "unknown".equalsIgnoreCase(trimmed) ? "" : trimmed;
	}

	private static JSONObject readOnlineRecord(StringRedisTemplate redisTemplate, String tokenValue) {
		if (redisTemplate == null || StrUtil.isBlank(tokenValue)) {
			return null;
		}
		try {
			String json = redisTemplate.opsForValue().get(CacheConstants.SYS_ONLINE_KEY + tokenValue.trim());
			if (StrUtil.isBlank(json)) {
				return null;
			}
			return JSONUtil.parseObj(json);
		}
		catch (Exception e) {
			log.warn("读取在线会话记录失败，设备信息/推送将省略: {}", e.getMessage());
			return null;
		}
	}

	private static Map<String, String> deviceOf(JSONObject deviceJson) {
		if (deviceJson == null || deviceJson.isEmpty()) {
			return null;
		}
		Map<String, String> device = new LinkedHashMap<>(5);
		copyIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_OS);
		copyIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_BROWSER);
		copyIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_LOCATION);
		copyIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_IP);
		copyIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_LOGIN_TIME);
		return device.isEmpty() ? null : device;
	}

	private static void copyIfPresent(JSONObject source, Map<String, String> target, String key) {
		String value = source.getStr(key);
		if (StrUtil.isNotBlank(value)) {
			target.put(key, value);
		}
	}

	private static StringRedisTemplate redisTemplateOrNull() {
		try {
			return SpringUtils.getBean(StringRedisTemplate.class);
		}
		catch (Exception e) {
			log.warn("获取 RedisTemplate 失败，会话失效原因降级: {}", e.getMessage());
			return null;
		}
	}

	/** 被踢 token 的推送路由信息 */
	private record PushTarget(String recipientId, String tenantId, String recipientType) {
	}

}
