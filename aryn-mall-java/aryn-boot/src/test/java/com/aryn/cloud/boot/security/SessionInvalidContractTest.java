package com.aryn.cloud.boot.security;

import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.SessionInvalidPushConstants;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.core.util.SessionInvalidPayload;
import com.aryn.cloud.common.core.util.SessionInvalidResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话失效原因契约测试。
 *
 * <p>背景：用户被顶下线时前端只能看到"登录已过期"并直接被踢回登录页。根因是 Sa-Token 的 7 种
 * 未登录场景值只被揉进中文 {@code msg}（且可能拼上完整 token），三个 401 出口还不一致
 * （过滤器 code=401、MVC 兜底 code=1）。本契约把「reason 枚举 + 脱敏文案 + data 结构 + 三个出口
 * 同源 + Redis 失效记录 key/TTL」绑在一起，避免以后任何一侧单独漂移。</p>
 *
 * <p>契约正文见 {@code docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md}。</p>
 */
class SessionInvalidContractTest {

	private static final String BOOT_FILTER = "aryn-boot/src/main/java/com/aryn/cloud/config/SaTokenConfigure.java";

	private static final String GATEWAY_FILTER = "aryn-gateway/src/main/java/com/aryn/cloud/gateway/config/SaTokenConfigure.java";

	private static final String MVC_HANDLER = "aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/handler/ArynGlobalExceptionHandlerResolver.java";

	private static final String SUPPORT = "aryn-common/aryn-common-security/src/main/java/com/aryn/cloud/common/security/support/SessionInvalidSupport.java";

	private static final String PUSH_EVENT_DTO = "aryn-message/aryn-message-api/src/main/java/com/aryn/cloud/message/api/dto/push/MessagePushEvent.java";

	private static final String PUSH_SERVICE = "aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/service/impl/MessagePushServiceImpl.java";

	private static final String AUTH_SESSION_CONTROLLER = "aryn-auth/src/main/java/com/aryn/cloud/auth/controller/SessionDeviceController.java";

	private static final String LOGIN_LOG_CONTROLLER = "aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysLoginLogController.java";

	private final Path projectRoot = findProjectRoot();

	@Test
	void reasonWireValuesAndMessagesAreFrozen() {
		assertThat(SessionInvalidReason.values()).extracting(Enum::name)
			.containsExactly("NOT_TOKEN", "TOKEN_INVALID", "TOKEN_EXPIRED", "BE_REPLACED", "KICK_OUT",
					"TOKEN_FREEZE", "NO_PREFIX");
		assertThat(SessionInvalidReason.BE_REPLACED.getMessage()).isEqualTo("账号已在其他设备登录");
		assertThat(SessionInvalidReason.TOKEN_EXPIRED.getMessage()).isEqualTo("登录已过期，请重新登录");
		assertThat(SessionInvalidReason.KICK_OUT.getMessage()).isEqualTo("登录状态已失效，请重新登录");
		// 文案必须脱敏：历史实现会把完整 token 拼进 msg 返回给客户端
		for (SessionInvalidReason reason : SessionInvalidReason.values()) {
			assertThat(reason.getMessage().toLowerCase())
				.as("reason=%s 的 msg 不得包含 token 字样", reason.name())
				.doesNotContain("token");
		}
	}

	@Test
	void saTokenSceneTypeMapsToEveryReason() {
		assertThat(SessionInvalidReason.fromSaTokenType("-1")).isEqualTo(SessionInvalidReason.NOT_TOKEN);
		assertThat(SessionInvalidReason.fromSaTokenType("-2")).isEqualTo(SessionInvalidReason.TOKEN_INVALID);
		assertThat(SessionInvalidReason.fromSaTokenType("-3")).isEqualTo(SessionInvalidReason.TOKEN_EXPIRED);
		assertThat(SessionInvalidReason.fromSaTokenType("-4")).isEqualTo(SessionInvalidReason.BE_REPLACED);
		assertThat(SessionInvalidReason.fromSaTokenType("-5")).isEqualTo(SessionInvalidReason.KICK_OUT);
		assertThat(SessionInvalidReason.fromSaTokenType("-6")).isEqualTo(SessionInvalidReason.TOKEN_FREEZE);
		assertThat(SessionInvalidReason.fromSaTokenType("-7")).isEqualTo(SessionInvalidReason.NO_PREFIX);
		assertThat(SessionInvalidReason.fromSaTokenType("-99")).isEqualTo(SessionInvalidReason.TOKEN_INVALID);
		assertThat(SessionInvalidReason.fromSaTokenType(null)).isEqualTo(SessionInvalidReason.TOKEN_INVALID);
		// Redis 记录里存的是 wire 值（枚举名），读取时做防御性解析
		assertThat(SessionInvalidReason.fromWire("be_replaced")).isEqualTo(SessionInvalidReason.BE_REPLACED);
		assertThat(SessionInvalidReason.fromWire("unknown")).isEqualTo(SessionInvalidReason.TOKEN_INVALID);
	}

	@Test
	void payloadOnlyExposesWhitelistedDeviceFields() {
		Map<String, String> raw = new LinkedHashMap<>();
		raw.put("os", "Windows 10");
		raw.put("browser", "Chrome 120");
		raw.put("location", "上海市");
		raw.put("loginTime", "2026-10-28T21:32:05+08:00");
		// 2026-11-01 产品决策：device.ip 允许下发，帮助用户识别新登录设备；键名必须是 ip
		raw.put("ip", "1.2.3.4");
		raw.put("ipAddr", "9.9.9.9");
		raw.put("tokenId", "secret-token");
		raw.put("tenantId", "t1");
		raw.put("userName", "admin");

		Map<String, Object> data = SessionInvalidPayload.of(SessionInvalidReason.BE_REPLACED,
				"2026-10-28T21:32:07+08:00", raw);

		assertThat(data).containsEntry(SessionInvalidPayload.REASON, "BE_REPLACED")
			.containsEntry(SessionInvalidPayload.INVALID_AT, "2026-10-28T21:32:07+08:00");
		@SuppressWarnings("unchecked")
		Map<String, String> device = (Map<String, String>) data.get(SessionInvalidPayload.DEVICE);
		assertThat(device).containsOnlyKeys(SessionInvalidPayload.DEVICE_OS, SessionInvalidPayload.DEVICE_BROWSER,
				SessionInvalidPayload.DEVICE_LOCATION, SessionInvalidPayload.DEVICE_IP,
				SessionInvalidPayload.DEVICE_LOGIN_TIME)
			.containsEntry(SessionInvalidPayload.DEVICE_IP, "1.2.3.4");
		String json = JSONUtil.toJsonStr(data);
		assertThat(json).doesNotContain("secret-token")
			.as("非白名单键 ipAddr 及其值不得透传（IP 只认 device.ip）")
			.doesNotContain("9.9.9.9")
			.doesNotContain("ipAddr")
			.doesNotContain("tenantId")
			.doesNotContain("token");
	}

	@Test
	void payloadOmitsEmptyFieldsAndTruncatesLongDeviceValues() {
		Map<String, Object> withoutDevice = SessionInvalidPayload.of(SessionInvalidReason.TOKEN_EXPIRED, "now", null);
		assertThat(withoutDevice).doesNotContainKey(SessionInvalidPayload.DEVICE);

		Map<String, Object> fallbackReason = SessionInvalidPayload.of(null, null, null);
		assertThat(fallbackReason).containsEntry(SessionInvalidPayload.REASON, "TOKEN_INVALID")
			.doesNotContainKey(SessionInvalidPayload.INVALID_AT);

		Map<String, String> longDevice = new LinkedHashMap<>();
		longDevice.put(SessionInvalidPayload.DEVICE_OS, "x".repeat(200));
		Map<String, Object> truncated = SessionInvalidPayload.of(SessionInvalidReason.BE_REPLACED, null, longDevice);
		@SuppressWarnings("unchecked")
		Map<String, String> device = (Map<String, String>) truncated.get(SessionInvalidPayload.DEVICE);
		assertThat(device.get(SessionInvalidPayload.DEVICE_OS)).hasSize(40);
	}

	@Test
	void sessionInvalidResponseBodyIsValidNestedJson() {
		// 2026-10-29 boot 部署验证发现的真实缺陷：Sa-Token 过滤器用 String.valueOf(errorHandler.run(e))
		// 写响应，而 SaResult#toString() 对 data 只做 String.valueOf(map)，嵌套 data 会变成
		// {reason=NOT_TOKEN}（非法 JSON），浏览器 Axios 无法解析。因此过滤器必须返回
		// SessionInvalidResponse.toJson(...) 预序列化字符串，这里守住"响应体是合法嵌套 JSON"。
		Map<String, String> device = new LinkedHashMap<>();
		device.put(SessionInvalidPayload.DEVICE_OS, "Windows 10");
		device.put(SessionInvalidPayload.DEVICE_BROWSER, "Chrome 120");
		device.put(SessionInvalidPayload.DEVICE_LOCATION, "上海市");
		device.put(SessionInvalidPayload.DEVICE_LOGIN_TIME, "2026-10-28T21:32:05+08:00");
		String body = SessionInvalidResponse.toJson(SessionInvalidReason.BE_REPLACED,
				SessionInvalidPayload.of(SessionInvalidReason.BE_REPLACED, "2026-10-28T21:32:07+08:00", device));

		// 必须是合法 JSON 且 data 是对象（前端按 data.reason 消费）
		cn.hutool.json.JSONObject parsed = cn.hutool.json.JSONUtil.parseObj(body);
		assertThat(parsed.getInt("code")).isEqualTo(401);
		assertThat(parsed.getStr("msg")).isEqualTo(SessionInvalidReason.BE_REPLACED.getMessage());
		cn.hutool.json.JSONObject data = parsed.getJSONObject("data");
		assertThat(data.isEmpty()).as("data 必须是 JSON 对象").isFalse();
		assertThat(data.getStr("reason")).isEqualTo("BE_REPLACED");
		assertThat(data.getJSONObject("device").getStr("os")).isEqualTo("Windows 10");
		assertThat(body).doesNotContain("{reason=").doesNotContain("token");
	}

	@Test
	void subReasonWireValuesAndScopeAreFrozen() {
		assertThat(SessionInvalidSubReason.values()).extracting(Enum::name)
			.containsExactly("ADMIN_KICK", "ACCOUNT_DISABLED", "PASSWORD_CHANGED", "ACCOUNT_DELETED",
					"TENANT_DISABLED", "DELIVERY_REVOKED", "MEMBER_DISABLED");
		assertThat(SessionInvalidSubReason.fromWire("account_disabled"))
			.isEqualTo(SessionInvalidSubReason.ACCOUNT_DISABLED);
		assertThat(SessionInvalidSubReason.fromWire("FUTURE_UNKNOWN_REASON")).isNull();
		assertThat(SessionInvalidSubReason.fromWire(null)).isNull();

		// 只有 KICK_OUT 才输出 subReason
		Map<String, Object> kicked = SessionInvalidPayload.of(SessionInvalidReason.KICK_OUT,
				SessionInvalidSubReason.ACCOUNT_DISABLED, null, null);
		assertThat(kicked).containsEntry(SessionInvalidPayload.SUB_REASON, "ACCOUNT_DISABLED");
		Map<String, Object> replaced = SessionInvalidPayload.of(SessionInvalidReason.BE_REPLACED,
				SessionInvalidSubReason.ACCOUNT_DISABLED, null, null);
		assertThat(replaced).doesNotContainKey(SessionInvalidPayload.SUB_REASON);

		// 实际下发路径（预序列化 JSON 字符串）仍是嵌套字段，且不含凭证
		String body = SessionInvalidResponse.toJson(SessionInvalidReason.KICK_OUT, kicked);
		assertThat(cn.hutool.json.JSONUtil.parseObj(body).getJSONObject("data").getStr("subReason"))
			.isEqualTo("ACCOUNT_DISABLED");
		assertThat(body).doesNotContain("token");
	}

	@Test
	void everyKickoutCallSiteCarriesSubReason() throws IOException {
		Map<String, String> expectations = new LinkedHashMap<>();
		expectations.put("aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/SysUserSessionRevoker.java",
				"SessionInvalidSubReason");
		expectations.put("aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysOnlineUserController.java",
				"ADMIN_KICK");
		expectations.put("aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/support/TenantSessionRevoker.java",
				"TENANT_DISABLED");
		expectations.put("aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/service/impl/SysUserServiceImpl.java",
				"ACCOUNT_DISABLED");
		expectations.put("aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/controller/SysUserController.java",
				"PASSWORD_CHANGED");
		expectations.put("aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/service/impl/UserInfoServiceImpl.java",
				"MEMBER_DISABLED");
		expectations.put("aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/security/DeliveryAccessGuard.java",
				"DELIVERY_REVOKED");
		for (Map.Entry<String, String> entry : expectations.entrySet()) {
			assertThat(Files.readString(projectRoot.resolve(entry.getKey())))
				.as("%s 必须携带细分原因 %s", entry.getKey(), entry.getValue())
				.contains(entry.getValue());
		}
		// 网关按同一契约读出 subReason
		assertThat(Files.readString(projectRoot.resolve(GATEWAY_FILTER)))
			.contains("SessionInvalidSubReason.fromWire", "SessionInvalidPayload.SUB_REASON");
	}

	@Test
	void sessionInvalidPushContractIsFrozen() throws IOException {
		// WS 主动推送（三期）：通道/事件类型/接收者类型是跨模块契约，发布方 common-security 与
		// 投递方 message-biz 必须引用 common-core 常量，禁止两端各写一份字符串。
		assertThat(SessionInvalidPushConstants.CHANNEL).isEqualTo("message:push:events");
		assertThat(SessionInvalidPushConstants.EVENT_TYPE_SESSION_INVALID).isEqualTo("SESSION_INVALID");
		assertThat(SessionInvalidPushConstants.RECIPIENT_TYPE_SYS_USER).isEqualTo("SYS_USER");
		assertThat(SessionInvalidPushConstants.RECIPIENT_TYPE_MALL_USER).isEqualTo("MALL_USER");

		String support = Files.readString(projectRoot.resolve(SUPPORT));
		assertThat(support).contains("SessionInvalidPushConstants.CHANNEL", "convertAndSend",
				"UUID.randomUUID", "publishSessionInvalid");
		assertThat(Files.readString(projectRoot.resolve(PUSH_SERVICE)))
			.contains("SessionInvalidPushConstants.CHANNEL");
		// 事件 DTO 必须具备前端消费所需的可空字段（Jackson 反序列化靠字段名）
		assertThat(Files.readString(projectRoot.resolve(PUSH_EVENT_DTO)))
			.contains("private String reason", "private String subReason", "private String invalidAt",
					"private Map<String, String> device");
		// 契约文档冻结事件样例
		Path contract = projectRoot.getParent()
			.resolve("docs/50-需求文档/2026-10-28-被顶下线弹窗提示/三期实现契约.md");
		assertThat(contract).exists();
		assertThat(Files.readString(contract)).contains("SESSION_INVALID", "\"recipientType\"",
				"/auth/session/devices", "/sysloginlog/my");
	}

	@Test
	void sessionDeviceAndSelfLoginLogApisStayWithinSafetyBounds() throws IOException {
		String deviceController = Files.readString(projectRoot.resolve(AUTH_SESSION_CONTROLLER));
		assertThat(deviceController)
			.as("登录设备接口：只读自身会话 + token 只以 hash 出现")
			.contains("@RequestMapping(\"/session\")", "@GetMapping(\"/devices\")",
					"@DeleteMapping(\"/devices/{id}\")", "DigestUtil.sha256Hex", "当前设备请使用退出登录")
			.as("不得在响应里返回原始 token")
			.doesNotContain("setTokenId");

		String loginLogController = Files.readString(projectRoot.resolve(LOGIN_LOG_CONTROLLER));
		assertThat(loginLogController)
			.as("我的登录记录：自服务口径（当前账号 + 当前租户），不加租户级权限点")
			.contains("@GetMapping(\"/my\")", "SecurityUtils.requireUser", "SysLoginLog::getUserName",
					"SysLoginLog::getTenantId");
	}

	@Test
	void bootAndGatewayFiltersShareTheSameResponseContract() throws IOException {
		String bootFilter = Files.readString(projectRoot.resolve(BOOT_FILTER));
		String gatewayFilter = Files.readString(projectRoot.resolve(GATEWAY_FILTER));

		assertThat(bootFilter).as("boot 过滤器必须按场景值映射原因并使用脱敏文案")
			.contains("NotLoginException")
			.contains("SessionInvalidReason.fromSaTokenType")
			.contains("SessionInvalidSupport.resolveData")
			.contains("SessionInvalidResponse.toJson")
			.as("SaResult 无法序列化嵌套 data（部署验证实际复现），必须返回预序列化 JSON 字符串")
			.doesNotContain("setData(SessionInvalidSupport.resolveData")
			.as("不得再回显历史中文 msg")
			.doesNotContain("token已被顶下线")
			.doesNotContain("token已被踢下线");

		assertThat(gatewayFilter).as("cloud 网关必须下发同一份 reason/data 契约")
			.contains("NotLoginException")
			.contains("SessionInvalidReason.fromSaTokenType")
			.contains("CacheConstants.SESSION_INVALID_KEY")
			.contains("SessionInvalidPayload")
			.contains("SessionInvalidResponse.toJson")
			.doesNotContain("token已被顶下线");
	}

	@Test
	void mvcFallbackHandlerReturns401WithReasonInsteadOfCodeOne() throws IOException {
		String handler = Files.readString(projectRoot.resolve(MVC_HANDLER));
		assertThat(handler).as("兜底 handler 必须与过滤器同契约，消灭 code=1 的假登录")
			.contains("SessionInvalidReason.fromSaTokenType")
			.contains("SessionInvalidSupport.resolveData")
			.contains("HttpStatus.UNAUTHORIZED.value()")
			.doesNotContain("Result.fail(message)")
			.doesNotContain("当前会话未登录");
	}

	@Test
	void supportUsesTokenScopedRedisKeyAndSevenDayTtl() throws IOException {
		String support = Files.readString(projectRoot.resolve(SUPPORT));
		assertThat(support).contains("CacheConstants.SESSION_INVALID_KEY")
			.contains("CacheConstants.SYS_ONLINE_KEY")
			.contains("TTL_SECONDS = 7L * 24 * 3600")
			.contains("BE_REPLACED")
			.contains("SessionInvalidPayload.cleanDevice")
			.as("读取/写入都用 token 维度 key，不得写入租户段")
			.doesNotContain("SESSION_INVALID_KEY + tenantId");
	}

	@Test
	void gatewayDoesNotDependOnServletOnlySecurityModule() throws IOException {
		String gatewayPom = Files.readString(projectRoot.resolve("aryn-gateway/pom.xml"));
		assertThat(gatewayPom).as("gateway 是 WebFlux 应用，不能依赖 servlet 体系的 common-security")
			.doesNotContain("aryn-common-security");
		assertThat(Files.readString(projectRoot.resolve(GATEWAY_FILTER)))
			.as("因此网关按同一契约直接读 Redis，而不是复用 common-security 的工具类")
			.contains("StringRedisTemplate");
	}

	@Test
	void contractDocStillDescribesTheFrozenValues() throws IOException {
		// findProjectRoot 定位到 aryn-mall-java，文档在仓库根目录的 docs/ 下
		Path repositoryRoot = projectRoot.getParent();
		assertThat(repositoryRoot).isNotNull();
		Path contract = repositoryRoot.resolve(
				"docs/50-需求文档/2026-10-28-被顶下线弹窗提示/一期实现契约.md");
		assertThat(contract).exists();
		String doc = Files.readString(contract);
		for (SessionInvalidReason reason : List.of(SessionInvalidReason.BE_REPLACED,
				SessionInvalidReason.TOKEN_EXPIRED, SessionInvalidReason.KICK_OUT, SessionInvalidReason.TOKEN_INVALID)) {
			assertThat(doc).as("契约文档必须登记 reason=%s", reason.name()).contains(reason.name());
			assertThat(doc).contains(reason.getMessage());
		}
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("未找到包含 db/cloud 的项目根目录");
		}
		return current;
	}

}
