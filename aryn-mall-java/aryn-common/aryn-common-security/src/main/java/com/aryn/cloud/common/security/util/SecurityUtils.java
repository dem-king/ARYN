
package com.aryn.cloud.common.security.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.experimental.UtilityClass;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;

/**
 * 安全工具类
 *
 * @author 雨滴kian
 * @since 2022/11/25 21:25
 */
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
