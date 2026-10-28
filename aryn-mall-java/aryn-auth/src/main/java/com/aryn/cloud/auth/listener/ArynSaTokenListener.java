
package com.aryn.cloud.auth.listener;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.JakartaServletUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.aryn.cloud.common.log.event.ArynLoginLogEvent;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.upms.api.entity.SysUserOnline;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token 侦听器
 *
 * @author 雨滴kian
 * @date 2022/7/8
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynSaTokenListener implements SaTokenListener {

	private final ApplicationEventPublisher applicationEventPublisher;

	private final StringRedisTemplate redisTemplate;

	/** 每次登录时触发：所有端都写在线记录，登录日志仍只记录管理端 */
	@Override
	public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginModel) {
		ArynUser hxUser = (ArynUser) SaHolder.getStorage().get(CacheConstants.USER_CACHE);
		if (hxUser == null || StrUtil.isBlank(hxUser.getTenantId())) {
			return;
		}
		HttpServletRequest request = currentRequest();
		String agent = request != null ? request.getHeader("User-Agent") : null;
		UserAgent ua = UserAgentUtil.parse(StrUtil.blankToDefault(agent, ""));
		String os = ua.getOs() != null ? ua.getOs().toString() : null;
		String browser = ua.getBrowser() != null ? ua.getBrowser().toString() : null;
		String ipAddr = request != null ? JakartaServletUtil.getClientIP(request) : null;
		String location = ipAddr != null ? IpUtils.getWhoisAddress(ipAddr) : null;
		boolean tob = DeviceTypeEnum.TOB.getDevice().equals(loginModel.getDeviceType());

		SysLoginLogBase sysLoginLog = null;
		if (tob) {
			sysLoginLog = new SysLoginLogBase();
			sysLoginLog.setStatus(CommonConstants.LOGIN_LOG_STATUS_1);
			sysLoginLog.setIpAddr(ipAddr);
			sysLoginLog.setLocation(location);
			sysLoginLog.setCreateBy(hxUser.getUsername());
			sysLoginLog.setOs(os);
			sysLoginLog.setBrowser(browser);
			sysLoginLog.setMsg(StrUtil.format("用户：{} 登录成功", hxUser.getUsername()));
			sysLoginLog.setUserName(hxUser.getUsername());
			sysLoginLog.setTenantId(hxUser.getTenantId());
		}

		// 在线记录覆盖 TOB/TOC：租户停用或删除时需要按 tenantId 撤销该租户全部会话
		SysUserOnline sysUserOnline = new SysUserOnline();
		sysUserOnline.setTokenId(tokenValue);
		sysUserOnline.setTokenTimeout(loginModel.getTimeout());
		sysUserOnline.setBrowser(browser);
		sysUserOnline.setOs(os);
		sysUserOnline.setUserName(hxUser.getUsername());
		sysUserOnline.setLoginTime(LocalDateTime.now());
		sysUserOnline.setIpAddr(ipAddr);
		sysUserOnline.setLocation(location);
		sysUserOnline.setTenantId(hxUser.getTenantId());
		sysUserOnline.setDeviceType(loginModel.getDeviceType());
		String key = onlineUserKey(tokenValue);
		String value = JSONUtil.toJsonStr(sysUserOnline);
		if (loginModel.getTimeout() > 0) {
			redisTemplate.opsForValue().set(key, value, loginModel.getTimeout(), TimeUnit.SECONDS);
		}
		else {
			redisTemplate.opsForValue().set(key, value);
		}

		if (sysLoginLog == null) {
			return;
		}
		try {
			applicationEventPublisher.publishEvent(new ArynLoginLogEvent(this, sysLoginLog));
		}
		catch (Exception e) {
			log.error(e.getMessage());
		}
	}

	private HttpServletRequest currentRequest() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		return attributes != null ? attributes.getRequest() : null;
	}

	@Override
	public void doLogout(String loginType, Object loginId, String tokenValue) {
		deleteOnlineUser(tokenValue);
	}

	@Override
	public void doKickout(String loginType, Object loginId, String tokenValue) {
		deleteOnlineUser(tokenValue);
	}

	@Override
	public void doReplaced(String loginType, Object loginId, String tokenValue) {
		deleteOnlineUser(tokenValue);
	}

	/**
	 * 在线会话记录的 Redis key。
	 *
	 * <p>历史实现写入时是 {@code 前缀 + tenantId + ":" + token}，而注销/强退/顶号回调里
	 * 用 {@code 前缀 + token} 删除，key 永远不匹配，导致已下线的会话残留在「在线用户」
	 * 列表里直到 30 天 TTL。租户信息已保存在值（{@link SysUserOnline#getTenantId()}）中，
	 * key 不再重复携带租户，保证写入与删除使用同一 key。
	 */
	static String onlineUserKey(String tokenValue) {
		return CacheConstants.SYS_ONLINE_KEY + tokenValue;
	}

	private void deleteOnlineUser(String tokenValue) {
		if (StrUtil.isBlank(tokenValue)) {
			return;
		}
		redisTemplate.delete(onlineUserKey(tokenValue));
	}

	@Override
	public void doDisable(String s, Object o, String s1, int i, long l) {
	}

	@Override
	public void doUntieDisable(String s, Object o, String s1) {
	}

	@Override
	public void doOpenSafe(String s, String s1, String s2, long l) {
	}

	@Override
	public void doCloseSafe(String s, String s1, String s2) {
	}

	@Override
	public void doCreateSession(String s) {
	}

	@Override
	public void doLogoutSession(String s) {
	}

	@Override
	public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long timeout) {
		// token 续期时同步续期在线记录，否则记录先过期，租户停用时无法再定位到该会话
		if (StrUtil.isBlank(tokenValue)) {
			return;
		}
		if (timeout > 0) {
			redisTemplate.expire(onlineUserKey(tokenValue), timeout, TimeUnit.SECONDS);
		}
	}

}
