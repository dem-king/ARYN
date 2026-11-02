
package com.aryn.cloud.auth.service;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.aryn.cloud.auth.config.LoginProtectionProperties;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.aryn.cloud.common.log.event.ArynLoginLogEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * 管理端（TOB）账号登录。
 *
 * <p>安全约束（2026-10-28 加固）：</p>
 * <ol>
 *   <li>账号不存在与密码错误统一返回「账号或密码错误」，不暴露账号是否存在；</li>
 *   <li>登录失败按「账号 + IP」双维度计数限流，达到上限临时锁定（{@link LoginAttemptGuard}）；</li>
 *   <li>登录成功前清空会话快照中的密码 hash，避免 hash 随 token-session 写入 Redis；</li>
 *   <li>管理端会话绝对有效期由 {@code security.login.tob-timeout-seconds} 控制（默认 12 小时）。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginService {

	/** 统一的登录失败提示：账号不存在 / 密码错误 / 密码为空共用，避免账号枚举 */
	private static final String LOGIN_FAIL_MESSAGE = "账号或密码错误";

	@DubboReference
	private final RemoteSysUserService remoteSysUserService;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final TenantAccessGuard tenantAccessGuard;

	private final LoginAttemptGuard loginAttemptGuard;

	private final LoginProtectionProperties loginProtectionProperties;

	public SaTokenInfo login(String username, String password) {
		String account = normalizeAccount(username);
		String clientIp = currentClientIp();
		assertNotLocked(account, clientIp);
		SysUser result = remoteSysUserService.getUserInfo(username);
		ArynUser hxUser = getUserDetails(result);
		if (ObjectUtil.isNull(hxUser)) {
			loginAttemptGuard.recordFailure(account, clientIp);
			throw new ArynBusinessException(LOGIN_FAIL_MESSAGE);
		}
		if (!passwordMatches(password, hxUser.getPassword())) {
			loginAttemptGuard.recordFailure(account, clientIp);
			saveLoginLog(hxUser, LOGIN_FAIL_MESSAGE);
			throw new ArynBusinessException(LOGIN_FAIL_MESSAGE);
		}
		if (!CommonConstants.NORMAL_STATUS.equals(hxUser.getStatus())) {
			saveLoginLog(hxUser, "状态异常不可登录");
			throw new ArynBusinessException("状态异常不可登录");
		}
		tenantAccessGuard.assertAvailable(hxUser.getTenantId());
		loginAttemptGuard.resetAccount(account);
		// 密码 hash 不能进入会话快照（会随 token-session 落到 Redis）
		hxUser.setPassword(null);
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOB, tobLoginParameter());
		return StpUtil.getTokenInfo();
	}

	public SaTokenInfo smsLogin(String phone) {
		SysUser result = remoteSysUserService.getUserInfoByPhone(phone);
		ArynUser hxUser = getUserDetails(result);
		if (ObjectUtil.isNull(hxUser)) {
			throw new ArynBusinessException("用户不存在");
		}
		if (!CommonConstants.NORMAL_STATUS.equals(hxUser.getStatus())) {
			saveLoginLog(hxUser, "状态异常不可登录");
			throw new ArynBusinessException("状态异常不可登录");
		}
		tenantAccessGuard.assertAvailable(hxUser.getTenantId());
		hxUser.setPassword(null);
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOB, tobLoginParameter());
		return StpUtil.getTokenInfo();
	}

	/** 配送员使用系统员工账号登录，配送资格由独立权限控制。 */
	public SaTokenInfo deliveryLogin(String phone, String password) {
		String account = normalizeAccount(phone);
		String clientIp = currentClientIp();
		assertNotLocked(account, clientIp);
		SysUser result = remoteSysUserService.getUserInfoByPhone(phone);
		if (ObjectUtil.isNull(result)) {
			loginAttemptGuard.recordFailure(account, clientIp);
			throw new ArynBusinessException(LOGIN_FAIL_MESSAGE);
		}
		ArynUser hxUser = getUserDetails(result);
		if (!passwordMatches(password, hxUser.getPassword())) {
			loginAttemptGuard.recordFailure(account, clientIp);
			throw new ArynBusinessException(LOGIN_FAIL_MESSAGE);
		}
		if (!CommonConstants.NORMAL_STATUS.equals(hxUser.getStatus())) {
			throw new ArynBusinessException("状态异常不可登录");
		}
		tenantAccessGuard.assertAvailable(hxUser.getTenantId());
		if (hxUser.getPermissions() == null
				|| (!hxUser.getPermissions().contains("delivery:execute") && !hxUser.getPermissions().contains("*"))) {
			throw new ArynBusinessException("当前账号未开通配送权限");
		}
		loginAttemptGuard.resetAccount(account);
		hxUser.setPassword(null);
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOB, tobLoginParameter());
		return StpUtil.getTokenInfo();
	}

	/**
	 * 密码比对：空密码、库中 hash 非法都按「不匹配」处理，避免 BCrypt 抛异常后返回 500。
	 */
	private boolean passwordMatches(String rawPassword, String passwordHash) {
		if (StrUtil.isBlank(rawPassword) || StrUtil.isBlank(passwordHash)) {
			return false;
		}
		try {
			return BCrypt.checkpw(rawPassword, passwordHash);
		}
		catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * 判断当前是否已被登录失败锁定；锁定则返回统一提示（不区分账号是否存在）。
	 */
	private void assertNotLocked(String account, String clientIp) {
		long retryAfter = loginAttemptGuard.retryAfterSeconds(account, clientIp);
		if (retryAfter <= 0) {
			return;
		}
		if (retryAfter >= 60) {
			long minutes = (retryAfter + 59) / 60;
			throw new ArynBusinessException(StrUtil.format("登录失败次数过多，请 {} 分钟后再试", minutes));
		}
		throw new ArynBusinessException("登录失败次数过多，请稍后再试");
	}

	private static String normalizeAccount(String account) {
		return StrUtil.blankToDefault(account, "unknown").trim().toLowerCase(Locale.ROOT);
	}

	private static String currentClientIp() {
		ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		if (attributes == null || attributes.getRequest() == null) {
			return null;
		}
		return IpUtils.getClientIp(attributes.getRequest());
	}

	/**
	 * 管理端（含配送端，同为 TOB）本次登录的会话策略：只覆盖绝对有效期，
	 * 单点登录（isConcurrent/isShare）继续以全局配置为准。
	 */
	private SaLoginParameter tobLoginParameter() {
		return loginProtectionProperties.tobLoginParameter();
	}

	private void saveLoginLog(ArynUser hxUser, String msg) {
		try {
			HttpServletRequest request = ((ServletRequestAttributes) Objects
				.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
			// 处理登录日志
			String userName = hxUser.getUsername();
			String agent = StrUtil.blankToDefault(SaHolder.getRequest().getHeader("User-Agent"), "");
			UserAgent ua = UserAgentUtil.parse(agent);
			SysLoginLogBase sysLoginLog = new SysLoginLogBase();
			sysLoginLog.setStatus(CommonConstants.LOGIN_LOG_STATUS_0);
			sysLoginLog.setIpAddr(IpUtils.getClientIp(request));
			sysLoginLog.setLocation(IpUtils.getWhoisAddress(sysLoginLog.getIpAddr()));
			sysLoginLog.setCreateBy(userName);
			sysLoginLog.setOs(ua.getOs() != null ? ua.getOs().toString() : null);
			sysLoginLog.setBrowser(ua.getBrowser() != null ? ua.getBrowser().toString() : null);
			sysLoginLog.setMsg(StrUtil.format("用户: {} 登录失败，异常: {} ", userName, msg));
			sysLoginLog.setUserName(userName);
			ArynTenantContextHolder.setTenantId(hxUser.getTenantId());
			applicationEventPublisher.publishEvent(new ArynLoginLogEvent(this, sysLoginLog));
		}
		catch (Exception e) {
			// 登录日志失败不能影响主流程
			log.warn("写入登录失败日志异常 userName={}", hxUser.getUsername(), e);
		}
	}

	private ArynUser getUserDetails(SysUser result) {
		if (ObjectUtil.isNull(result)) {
			return null;
		}
		ArynUser hxUser = new ArynUser();
		BeanUtil.copyProperties(result, hxUser);
		hxUser.setUserId(result.getId());
		hxUser.setPermissions(result.getPermissions() == null ? new ArrayList<>() : new ArrayList<>(result.getPermissions()));
		return hxUser;
	}

}
