
package com.aryn.cloud.common.security.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.support.SessionInvalidSupport;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Objects;

/**
 * 安全工具类
 *
 * @author 雨滴kian
 * @since 2022/11/25 21:25
 */
@Slf4j
@UtilityClass
public class SecurityUtils {

	public static void login(ArynUser hxUser) {
		SaHolder.getStorage().set(CacheConstants.USER_CACHE, hxUser);
		StpUtil.login(hxUser.getUserId());
		StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, hxUser);
	}

	public static void loginByDevice(ArynUser hxUser, DeviceTypeEnum deviceTypeEnum) {
		loginByDevice(hxUser, deviceTypeEnum, null);
	}

	/**
	 * 按设备类型登录，并允许覆盖本次登录的 Sa-Token 参数（如管理端更短的会话有效期）。
	 *
	 * <p><b>不要</b>用该方法覆盖 {@code isConcurrent} / {@code isShare}：全局单点登录策略
	 * 以配置为准（见 docs/40-接口与风险/README.md「登录会话策略」），按入口覆盖会绕过单点约束。</p>
	 * @param hxUser 用户会话快照
	 * @param deviceTypeEnum 登录端类型
	 * @param loginParameter 本次登录参数；为 {@code null} 时沿用全局配置
	 */
	public static void loginByDevice(ArynUser hxUser, DeviceTypeEnum deviceTypeEnum, SaLoginParameter loginParameter) {
		// 登录会顶掉该账号同设备类型的旧会话（sa-token is-concurrent=false），
		// 登录前先记下旧 token，登录后给它们写"被顶下线"的失效原因，
		// 供旧设备下一次请求 401 时向前端说明原因并展示新设备信息。
		List<String> replacedTokens = collectReplacedTokens(hxUser, deviceTypeEnum);
		hxUser.setDeviceType(deviceTypeEnum);
		SaHolder.getStorage().set(CacheConstants.USER_CACHE, hxUser);
		if (loginParameter == null) {
			StpUtil.login(hxUser.getUserId(), deviceTypeEnum.getDevice());
		}
		else {
			loginParameter.setDeviceType(deviceTypeEnum.getDevice());
			StpUtil.login(hxUser.getUserId(), loginParameter);
		}
		StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, hxUser);
		markReplacedSessions(replacedTokens, hxUser, deviceTypeEnum);
	}

	/**
	 * 登录前捕获"即将被顶掉"的旧 token 列表；任何异常都只告警，登录不受影响。
	 */
	private static List<String> collectReplacedTokens(ArynUser hxUser, DeviceTypeEnum deviceTypeEnum) {
		try {
			return StpUtil.getTokenValueListByLoginId(hxUser.getUserId(), deviceTypeEnum.getDevice());
		}
		catch (Exception e) {
			log.warn("登录前读取旧会话失败，跳过失效原因记录: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * 为被顶掉的旧会话写失效原因（带新设备信息）；这是登录链路上的旁路能力，
	 * 任何失败都不能影响登录本身。
	 */
	private static void markReplacedSessions(List<String> replacedTokens, ArynUser hxUser, DeviceTypeEnum deviceTypeEnum) {
		if (replacedTokens == null || replacedTokens.isEmpty()) {
			return;
		}
		try {
			SessionInvalidSupport.markReplaced(replacedTokens, resolveNewTokenValue(hxUser, deviceTypeEnum),
					hxUser.getUserId(), hxUser.getTenantId(), deviceTypeEnum.getDevice());
		}
		catch (Exception e) {
			log.warn("记录被顶下线会话原因失败，不影响登录: {}", e.getMessage());
		}
	}

	/**
	 * 解析本次登录签发的新 token（用于读取新会话的在线记录作为"新设备信息"）。
	 */
	private static String resolveNewTokenValue(ArynUser hxUser, DeviceTypeEnum deviceTypeEnum) {
		try {
			String tokenValue = StpUtil.getTokenValue();
			if (StrUtil.isNotBlank(tokenValue)) {
				return tokenValue;
			}
		}
		catch (Exception ignored) {
			// 继续走按 loginId 反查的兜底路径
		}
		try {
			return StpUtil.getTokenValueByLoginId(hxUser.getUserId(), deviceTypeEnum.getDevice());
		}
		catch (Exception e) {
			return null;
		}
	}

	private boolean isWebRequest() {
		ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes) RequestContextHolder
			.getRequestAttributes();
		return Objects.isNull(servletRequestAttributes);
	}

	/**
	 * 获取用户信息
	 * @return
	 */
	public ArynUser getUser() {
		if (isWebRequest()) {
			return null;
		}
		// 未登录直接返回
		if (!StpUtil.isLogin()) {
			return null;
		}
		return (ArynUser) StpUtil.getTokenSession().get(CacheConstants.USER_CACHE);
	}

	public ArynUser requireUser() {
		ArynUser user = getUser();
		if (user == null) {
			throw new ArynBusinessException("当前会话未登录");
		}
		return user;
	}

	public ArynUser requireUser(DeviceTypeEnum deviceType) {
		ArynUser user = requireUser();
		if (user.getDeviceType() == null) {
			user.setDeviceType(parseDeviceType(StpUtil.getLoginDeviceType()));
		}
		return requireDevice(user, deviceType);
	}

	public ArynUser requireDevice(ArynUser user, DeviceTypeEnum deviceType) {
		if (user == null) {
			throw new ArynBusinessException("当前会话未登录");
		}
		if (deviceType == null || deviceType != user.getDeviceType()) {
			throw new ArynBusinessException("当前登录端无权访问该资源");
		}
		return user;
	}

	public String getUserId() {
		return requireUser().getUserId();
	}

	public String getOpenId() {
		return requireUser().getOpenId();
	}

	public String getTenantId() {
		return requireUser().getTenantId();
	}

	public DeviceTypeEnum getDeviceType() {
		ArynUser user = requireUser();
		DeviceTypeEnum deviceType = user.getDeviceType();
		return deviceType != null ? deviceType : parseDeviceType(StpUtil.getLoginDeviceType());
	}

	public DeviceTypeEnum parseDeviceType(String deviceType) {
		if (deviceType == null) {
			return null;
		}
		for (DeviceTypeEnum value : DeviceTypeEnum.values()) {
			if (value.getDevice().equalsIgnoreCase(deviceType)) {
				return value;
			}
		}
		return null;
	}

}
