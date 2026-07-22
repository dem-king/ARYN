
package com.aryn.cloud.common.security.util;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.StpUtil;
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
		hxUser.setDeviceType(deviceTypeEnum);
		SaHolder.getStorage().set(CacheConstants.USER_CACHE, hxUser);
		StpUtil.login(hxUser.getUserId(), deviceTypeEnum.getDevice());
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
