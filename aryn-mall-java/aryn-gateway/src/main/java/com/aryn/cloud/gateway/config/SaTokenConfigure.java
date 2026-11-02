
package com.aryn.cloud.gateway.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.core.properties.CorsProperties;
import com.aryn.cloud.common.core.util.SessionInvalidPayload;
import com.aryn.cloud.common.core.util.SessionInvalidResponse;
import com.aryn.cloud.gateway.properties.PermitAllUrlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [Sa-Token 权限认证] 配置类
 *
 * @author 雨滴kian
 * @date 2022/6/28
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class SaTokenConfigure {

	private final PermitAllUrlProperties permitAllUrlProperties;

	private final CorsProperties corsProperties;

	/** 读取 token 维度的会话失效原因（与 boot 侧 SessionInvalidRegistry 同一个 Redis key/JSON 结构） */
	private final StringRedisTemplate stringRedisTemplate;

	/**
	 * 注册 Sa-Token全局过滤器
	 *
	 * @author 雨滴kian
	 * @date 2022/6/28
	 * @return: cn.dev33.satoken.reactor.filter.SaReactorFilter
	 */
	@Bean
	public SaReactorFilter getSaReactorFilter() {
		return new SaReactorFilter()
			// 拦截地址
			.addInclude("/**")
			// 开放地址
			.addExclude("/favicon.ico")
			// 鉴权方法：每次访问进入
			.setAuth(obj -> {
				List<String> ignoreUrls = permitAllUrlProperties.getUrls();
				// 登录认证：除白名单路径外均需要登录认证
				SaRouter.match("/**").notMatch(ignoreUrls).check(StpUtil::checkLogin);
			})
			// 异常处理方法：每次setAuth函数出现异常时进入
			.setError(e -> {
				// 与 boot 侧 SaTokenConfigure 同一份契约：会话失效下发机器可读原因
				// （被顶下线时附新设备信息），msg 使用脱敏固定文案，不回显 token。
				if (e instanceof NotLoginException notLoginException) {
					SessionInvalidReason reason = SessionInvalidReason.fromSaTokenType(notLoginException.getType());
					// 同 boot：过滤器用 String.valueOf() 写响应，SaResult 的嵌套 data 不是合法 JSON，
					// 因此显式序列化为 JSON 字符串返回。
					SaHolder.getResponse().setHeader("Content-Type", SessionInvalidResponse.CONTENT_TYPE_JSON);
					return SessionInvalidResponse.toJson(reason, buildSessionInvalidData(reason));
				}
				return SaResult.error(e.getMessage()).setCode(HttpStatus.UNAUTHORIZED.value());
			}) // 前置函数：在每次认证函数之前执行
			.setBeforeAuth(obj -> {
				String origin = SaHolder.getRequest().getHeader("Origin");
				if (corsProperties.isAllowedOrigin(origin)) {
					SaHolder.getResponse()
						.setHeader("Access-Control-Allow-Origin", origin)
						.setHeader("Vary", "Origin")
						.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS")
						.setHeader("Access-Control-Allow-Headers", corsProperties.getAllowedHeadersValue())
						.setHeader("Access-Control-Max-Age", "3600");
				}

				// 如果是预检请求，则立即返回到前端
				SaRouter.match(SaHttpMethod.OPTIONS).free(r -> log.debug("OPTIONS预检请求，不做处理")).back();
			});

	}

	/**
	 * 组装 401 响应的 data：reason + 失效时间 + 被顶下线时的新设备信息。
	 *
	 * <p>gateway 是 WebFlux 应用，不能依赖 servlet 体系的 {@code aryn-common-security}，
	 * 因此这里按同一份契约（{@link SessionInvalidPayload} + {@link CacheConstants#SESSION_INVALID_KEY}）
	 * 直接读 Redis；读取失败只降级为"无原因/无设备信息"，不能影响 401 的返回。</p>
	 */
	private Map<String, Object> buildSessionInvalidData(SessionInvalidReason reason) {
		try {
			String tokenValue = currentTokenValue();
			if (StrUtil.isBlank(tokenValue)) {
				return SessionInvalidPayload.of(reason, null, null);
			}
			String json = stringRedisTemplate.opsForValue()
				.get(CacheConstants.SESSION_INVALID_KEY + tokenValue.trim());
			if (StrUtil.isBlank(json)) {
				return SessionInvalidPayload.of(reason, null, null);
			}
			JSONObject record = JSONUtil.parseObj(json);
			JSONObject deviceJson = record.getJSONObject(SessionInvalidPayload.DEVICE);
			Map<String, String> device = null;
			if (deviceJson != null && !deviceJson.isEmpty()) {
				device = new LinkedHashMap<>(5);
				putIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_OS);
				putIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_BROWSER);
				putIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_LOCATION);
				putIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_IP);
				putIfPresent(deviceJson, device, SessionInvalidPayload.DEVICE_LOGIN_TIME);
			}
			return SessionInvalidPayload.of(reason,
				SessionInvalidSubReason.fromWire(record.getStr(SessionInvalidPayload.SUB_REASON)),
				record.getStr(SessionInvalidPayload.INVALID_AT), device);
		}
		catch (Exception e) {
			log.warn("网关组装会话失效原因失败，按无原因降级: {}", e.getMessage());
			return SessionInvalidPayload.of(reason, null, null);
		}
	}

	private static void putIfPresent(JSONObject source, Map<String, String> target, String key) {
		String value = source.getStr(key);
		if (StrUtil.isNotBlank(value)) {
			target.put(key, value);
		}
	}

	/**
	 * 当前请求携带的原始 token；任何异常都吞掉（401 出口不能因为取不到 token 而恶化）。
	 */
	private static String currentTokenValue() {
		try {
			String tokenValue = StpUtil.getTokenValue();
			if (StrUtil.isNotBlank(tokenValue)) {
				return tokenValue;
			}
		}
		catch (Exception ignored) {
			// 继续走请求头兜底
		}
		try {
			return SaHolder.getRequest().getHeader(SaManager.getConfig().getTokenName());
		}
		catch (Exception e) {
			return null;
		}
	}

}
